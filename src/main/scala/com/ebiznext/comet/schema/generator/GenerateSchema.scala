package com.ebiznext.comet.schema.generator

import java.io.File
import java.util.regex.Pattern

import com.ebiznext.comet.schema.model._
import org.apache.poi.ss.usermodel.{DataFormatter, Row, Workbook, WorkbookFactory}

import scala.collection.JavaConverters._
import scala.util.{Success, Try}

object GenerateSchema {}

class XlsReader(path: String) {

  val workbook: Workbook = WorkbookFactory.create(new File(path))
  val formatter = new DataFormatter

  lazy val domain: Option[Domain] = {
    workbook.getSheet("domain").asScala.drop(1).headOption.flatMap { row =>
      val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .map(formatter.formatCellValue)
      val directoryOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .map(formatter.formatCellValue)
      val ack = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
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
            val quote = Some("\"") //TODO necessary ??
            val escape = Some("\\") // TODO necessary ??
            val metaData = Metadata(
              mode,
              format,
              None,
              None,
              None,
              withHeader,
              separator,
              quote,
              escape,
              write,
              None,
              None,
              None
            )
            Some(Schema(name, pattern, attributes = Nil, Some(metaData), None, None, None, None))
          }
          case _ => None
        }
      }
      .toList
  }

  // TODO vérifier le premier attribut positionnel et poser un flag indiquant si la première position commence par 0 ou 1 et instancier Position en conséquence
  def buildSchemas(): List[Schema] = {
    schemas.map { schema =>
      val schemaName = schema.name
      val attributes = workbook
        .getSheet(schemaName)
        .asScala
        .drop(1)
        .flatMap { row =>
          val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
          val renameOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
          val semTypeOpt = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
          val requiredOpt = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(_.toBoolean)
          val privacy = Option(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(PrivacyLevel.fromString)
          val metricType = Option(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(MetricType.fromString)

          val positionStart = Try(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(_.toInt) match {
            case Success(v) => v
            case _          => 0
          }
          val positionEnd = Try(row.getCell(7, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(_.toInt) match {
            case Success(v) => v
            case _          => 0
          }
          val positionTrim = Option(row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .map(formatter.formatCellValue)
            .map(Trim.fromString)

          (nameOpt, semTypeOpt, requiredOpt) match {
            case (Some(name), Some(semType), Some(required)) =>
              Some(
                Attribute(
                  name,
                  semType,
                  None,
                  required,
                  privacy,
                  position = Some(Position(positionStart, positionEnd, positionTrim)),
                  metricType = metricType
                )
              )
            case _ => None
          }
        }
        .toList
      schema.copy(attributes = attributes)
    }
  }
}

object ExcelReaderTest extends App {
  val reader = new XlsReader("data/DomainReferentiel.xlsx")

  val listSchemas = reader.buildSchemas()

  import YamlSerializer._
  reader.domain.foreach { d =>
    val domain = d.copy(schemas = listSchemas)
    println(s"Domain schemas:\r\n ${serialize(domain)}")
    serializeToFile(new File(s"data/${domain.name}.yml"), domain)
  }
}
