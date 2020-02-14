package com.ebiznext.comet.schema.generator

import com.ebiznext.comet.schema.model.{Attribute, Format, Position, Schema}
import com.ebiznext.comet.utils.{IBAN, PAN}

object PostEncryptSchemaGen {

  val encryptedSizeOpt: String => Option[Int] = (attType: String)=> attType.toUpperCase match {
    case "PAN" => Some(PAN.size)
    case "IBAN" => Some(IBAN.size)
    case _ => None
  }


  /**
    * Applies a ShiftCommand on the list of Attributes as follows :
    * - The Attribute whose Position.first is equal to the shiftCommand start value is incremented to match the
    *   encryptedSize of the shiftCommand
    * - The Atrributes whose Postion.first is greater than the shiftCommand start value are shifted by the delta of
    *   the shiftCommand
    * - The Attributes whose Postion.first is less than the shiftCommand start value remains unchanged
    *
    * @param attributes
    * @param shiftCommand
    * @return a new Attribute List with shifted positions
    */
  private def shiftAttributes(attributes:List[Attribute], shiftCommand: ShiftCommand): List[Attribute] = {
    attributes.map { attribute => {
      val newPos = attribute.position.map {
        case pos if pos.first == shiftCommand.start => Position(pos.first,pos.first+shiftCommand.encryptedSize-1, pos.trim)
        case pos if pos.first > shiftCommand.start => Position(pos.first+shiftCommand.delta,pos.last+shiftCommand.delta, pos.trim)
        case pos => pos
      }
      attribute.copy(position = newPos)
    }
    }
  }


  case class ShiftCommand(start: Int, delta: Int, encryptedSize:Int)

  /**
    * Calculates a List of optional ShiftCommands by mapping each attribute in the input Attributes List, as follows:
    * - if the attribute type has a corresponding encryptedSize, a ShiftCommand is built
    * - if the attribute type does not have an encryptedSize, the ShiftCommand is not needed
    * @param attributes
    * @return a List of ShiftCommands Options
    */
   def buildShiftCommands(attributes: List[Attribute]): List[Option[ShiftCommand]] = attributes.map {
    att =>
      encryptedSizeOpt(att.`type`).map{ encryptedSize =>
        val first = att.position.map(_.first).getOrElse(0)
        val last = att.position.map(_.last).getOrElse(0)
        val currentSize = (last-first)+1
        ShiftCommand(start = first,delta = encryptedSize - currentSize,encryptedSize = encryptedSize)
      }
  }

  /**
    * Builds a new list of Attributes by applyng sequentially the ShiftCommands on the input Attribute List
    * @param shiftCommands
    * @param attributes
    * @return a new List of Attributes
    */
  private def buildEncryptedAttributes(shiftCommands:List[Option[ShiftCommand]], attributes : List[Attribute]):List[Attribute]  = {
    shiftCommands match {
      case Nil => attributes
      case head :: tail => head match {
        case Some(sc) => {
          val shiftedAttributes = shiftAttributes(attributes,sc)
          val updatedCommands = tail.map(maybeShiftCommand => maybeShiftCommand.map(command => command.copy(start = command.start + sc.delta)))
          buildEncryptedAttributes(updatedCommands,shiftedAttributes)
        }
        case None => buildEncryptedAttributes(tail, attributes)
      }
    }
  }

  /**
    * Checks if a Post-Encryption Schema is necessary based on the format and the attributes types of the input Schema.
    * Builds and returns the new schema if it is the case, else wise returns None
    * @param schema
    * @return Optional Schema
    */
  def buildPostEncryptionSchema(schema:Schema): Option[Schema] = {
    val encryptionTypes = List("IBAN","PAN")
    schema.metadata.flatMap(_.format).exists(Format.POSITION.equals) && schema.attributes.map(_.`type`).map(_.toUpperCase).intersect(encryptionTypes).nonEmpty match {
      case true => {
        val shiftCommands = buildShiftCommands(schema.attributes)
        val newAttributes = buildEncryptedAttributes(shiftCommands, schema.attributes)
        Some(schema.copy(attributes = newAttributes))
      }
      case false => None
    }
  }

}
