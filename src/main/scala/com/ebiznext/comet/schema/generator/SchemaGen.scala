package com.ebiznext.comet.schema.generator

import java.io.File
import java.util.regex.Pattern

import com.ebiznext.comet.config.Settings
import com.ebiznext.comet.schema.model._
import com.typesafe.scalalogging.LazyLogging
import org.apache.poi.ss.usermodel.{DataFormatter, Row, Workbook, WorkbookFactory}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

object SchemaGen extends App with LazyLogging {

  def printUsage(): Unit = {
    println("""
        |Usage: SchemaGen <Excel file>
        |""".stripMargin)
  }

  def execute(path: String): Unit = {
    val reader = new XlsReader(path)

    val listSchemas = reader.buildSchemas().filter(_.attributes.nonEmpty)
    val encryptedSchemas = listSchemas.flatMap(PostEncryptSchemaGen.buildPostEncryptionSchema)
    import YamlSerializer._
    reader.domain.foreach { d =>
      val domain = d.copy(schemas = listSchemas)
      logger.info(s"""Generated schemas:
           |${serialize(domain)}""".stripMargin)
      val output_path = Settings.comet.metadata
      serializeToFile(new File(output_path, s"${domain.name}.yml"), domain)
      if (encryptedSchemas.nonEmpty) {
        val encryptedDomain = d.copy(schemas = encryptedSchemas)
        serializeToFile(
          new File(output_path, s"${encryptedDomain.name}-encrypted.yml"),
          encryptedDomain
        )
      }

    }
  }

  if (args.isEmpty) printUsage()
  else {
    logger.info("Start yaml schema generation")

    val inputFile = args.headOption
    inputFile match {
      case Some(path) => execute(path)
      case None       => printUsage()
    }
  }
}

class XlsReader(path: String) {

  val workbook: Workbook = WorkbookFactory.create(new File(path))
  val formatter = new DataFormatter

  lazy val domain: Option[Domain] = {
    workbook.getSheet("domain").asScala.drop(1).headOption.flatMap { row =>
      val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .map(formatter.formatCellValue)
      val directoryOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .map(formatter.formatCellValue)
      val ack = Option(row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
        .map(formatter.formatCellValue)
      val comment = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .map(formatter.formatCellValue)
      (nameOpt, directoryOpt) match {
        case (Some(name), Some(directory)) =>
          Some(Domain(name, directory, ack = ack, comment = comment))
        case _ => None
      }
    }
  }

  lazy val schemas: List[Schema] = {
    workbook
      .getSheet("schemas")
      .asScala
      .drop(1)
      .flatMap { row =>
        val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
        val patternOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
          .map(Pattern.compile)
        val mode: Option[Mode] = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
          .map(Mode.fromString)
        val write = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
          .map(WriteMode.fromString)
        val format = Option(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
          .map(Format.fromString)
        val withHeader = Option(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
          .map(_.toBoolean)
        val separator = Option(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .map(formatter.formatCellValue)
        

        (nameOpt, patternOpt) match {
          case (Some(name), Some(pattern)) => {
            val metaData = Metadata(
              mode,
              format,
              encoding = None,
              multiline = None,
              array = None,
              withHeader,
              separator,
              write = write,
              partition = None,
              index = None,
              properties = None
            )
            Some(Schema(name, pattern, attributes = Nil, Some(metaData), None, None, None, None))
          }
          case _ => None
        }
      }
      .toList
  }

  def buildSchemas(): List[Schema] = {
    schemas.map { schema =>
      val schemaName = schema.name
      val sheetOpt = Option(workbook.getSheet(schemaName))
      val attributes = sheetOpt match {
        case None => List.empty
        case Some(sheet) =>
          sheet.asScala
            .drop(1)
            .flatMap { row =>
              val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
              val renameOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
              val semTypeOpt = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
              val required = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
                .forall(_.toBoolean)
              val privacy = Option(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
                .map(PrivacyLevel.fromString)
              val metricType = Option(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
                .map(MetricType.fromString)
              val defaultOpt = Option(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)
              val commentOpt = Option(row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .map(formatter.formatCellValue)

              val positionOpt = schema.metadata.flatMap(_.format) match {
                case Some(Format.POSITION) => {
                  val positionStart =
                    Try(row.getCell(9, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                      .map(formatter.formatCellValue)
                      .map(_.toInt) match {
                      case Success(v) => v - 1
                      case _          => 0
                    }
                  val positionEnd = Try(row.getCell(10, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                    .map(formatter.formatCellValue)
                    .map(_.toInt) match {
                    case Success(v) => v - 1
                    case _          => 0
                  }
                  val positionTrim =
                    Option(row.getCell(11, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                      .map(formatter.formatCellValue)
                      .map(Trim.fromString)
                  Some(Position(positionStart, positionEnd, positionTrim))
                }
                case _ => None
              }

              (nameOpt, semTypeOpt) match {
                case (Some(name), Some(semType)) =>
                  Some(
                    Attribute(
                      name,
                      semType,
                      array = None,
                      required,
                      privacy,
                      comment = commentOpt,
                      rename = renameOpt,
                      metricType = metricType,
                      attributes = None,
                      position = positionOpt,
                      default = defaultOpt,
                      tags = None
                    )
                  )
                case _ => None
              }
            }
            .toList
      }
      schema.copy(attributes = attributes)
    }
  }
}
