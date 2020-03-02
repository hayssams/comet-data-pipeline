package com.ebiznext.comet.schema.generator

import java.util.regex.Pattern

import com.ebiznext.comet.schema.model._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{FlatSpec, Matchers}

class PostEncryptSchemaGenTest extends FlatSpec with Matchers with StrictLogging {

  val p1 = Position(0, 0, Some(Trim.BOTH))
  val p2 = Position(1, 5, Some(Trim.BOTH))
  val p3 = Position(6, 10, Some(Trim.BOTH))
  val p4 = Position(11, 13, Some(Trim.BOTH))
  val p5 = Position(14, 20, Some(Trim.BOTH))

  val a1 = Attribute("att1", position = Some(p1))
  val a2 = Attribute("att2", position = Some(p2), `type` = "IBAN")
  val a3 = Attribute("att3", position = Some(p3))
  val a4 = Attribute("att4", position = Some(p4), `type` = "PAN")
  val a5 = Attribute("att5", position = Some(p5))
  val attributes = List(a1, a2, a3, a4, a5)

  val positionMetadata = Metadata(Some(Mode.FILE), Some(Format.POSITION))
  val nonPositionMetadata = Metadata(Some(Mode.FILE), Some(Format.DSV))

  val withEncryptionSchema = Schema(
    "schemaWithEncryption",
    Pattern.compile("somefile.txt"),
    attributes,
    Some(positionMetadata),
    None,
    None,
    None,
    None
  )
  val noEncryptionSchema = withEncryptionSchema.copy(attributes = List(a1, a3, a5))
  val nonPositionSchema = withEncryptionSchema.copy(metadata = Some(nonPositionMetadata))

  "a schema of Position files with encrypted types" should "have a post encrypted schema with correctly shifted positions" in {
    val postEncryptSchema = PostEncryptSchemaGen.buildPostEncryptionSchema(withEncryptionSchema)
    postEncryptSchema shouldBe defined
    val attributes = postEncryptSchema.map(_.attributes)
    attributes shouldBe defined
    attributes.head.flatMap(_.position).map(_.first) shouldBe List(0, 1, 51, 56, 106)
    attributes.head.flatMap(_.position).map(_.last) shouldBe List(0, 50, 55, 105, 112)
  }

  "a shema with a format different than Position" should "not have a post encrypted schema" in {
    PostEncryptSchemaGen.buildPostEncryptionSchema(nonPositionSchema) shouldBe empty
  }

  "a shema with a Position format and encrypted type" should "not have a post encrypted schema" in {
    PostEncryptSchemaGen.buildPostEncryptionSchema(noEncryptionSchema) shouldBe empty
  }

  "a list of attributes with encryption types" should "have a non empty Shift Commands" in {
    PostEncryptSchemaGen.buildShiftCommands(attributes).filter(_.isDefined) should not be empty
  }

  "a list of attributes with no encryption types" should "have an empty Shift Commands" in {
    PostEncryptSchemaGen.buildShiftCommands(List(a1, a3, a5)).filter(_.isDefined) shouldBe empty
  }
}
