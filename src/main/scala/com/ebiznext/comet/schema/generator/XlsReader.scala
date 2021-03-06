package com.ebiznext.comet.schema.generator

import java.io.File
import java.util.regex.Pattern

import com.ebiznext.comet.config.Settings
import com.ebiznext.comet.schema.model._
import org.apache.poi.ss.usermodel._

import scala.collection.JavaConverters._

/**
  * Reads the spreadsheet found at the specified {@param path} and builds the corresponding Domain object
  * @param path
  */
class XlsReader(path: String) {

  private val workbook: Workbook = WorkbookFactory.create(new File(path))

  object formatter {
    private val f = new DataFormatter()

    def formatCellValue(cell: Cell): Option[String] = {
      f.formatCellValue(cell).trim match {
        case v if v.isEmpty => None
        case v              => Some(v)
      }
    }
  }

  private lazy val domain: Option[Domain] = {
    workbook.getSheet("domain").asScala.drop(1).headOption.flatMap { row =>
      val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .flatMap(formatter.formatCellValue)
      val directoryOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .flatMap(formatter.formatCellValue)
      // Here for ack, we do not want to get None returned for an empty cell since None would give us a ".ack" as default later on
      val ack = Option(row.getCell(2, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
        .flatMap(formatter.formatCellValue)
        .orElse(Some(""))
      val comment = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
        .flatMap(formatter.formatCellValue)
      (nameOpt, directoryOpt) match {
        case (Some(name), Some(directory)) =>
          Some(Domain(name, directory, ack = ack, comment = comment))
        case _ => None
      }
    }
  }

  private lazy val schemas: List[Schema] = {
    workbook
      .getSheet("schemas")
      .asScala
      .drop(1)
      .flatMap { row =>
        val nameOpt = Option(row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val patternOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
          .map(Pattern.compile)
        val mode: Option[Mode] = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
          .map(Mode.fromString)
        val write = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
          .map(WriteMode.fromString)
        val format = Option(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
          .map(Format.fromString)
        val withHeader = Option(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
          .map(_.toBoolean)
        val separator = Option(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val deltaColOpt = Option(row.getCell(7, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val identityKeysOpt = Option(row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val comment = Option(row.getCell(9, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val encodingOpt = Option(row.getCell(10, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
          .flatMap(formatter.formatCellValue)
        val partitionSamplingOpt =
          Option(row.getCell(11, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .flatMap(formatter.formatCellValue)
            .map(_.toDouble)
        val partitionColumnsOpt =
          Option(row.getCell(12, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .flatMap(formatter.formatCellValue)
            .map(_.split(",") map (_.trim))
            .map(_.toList)
        val sinkColumnsOpt =
          Option(row.getCell(13, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
            .flatMap(formatter.formatCellValue)
        (nameOpt, patternOpt) match {
          case (Some(name), Some(pattern)) => {
            val metaData = Metadata(
              mode,
              format,
              encoding = encodingOpt,
              multiline = None,
              array = None,
              withHeader,
              separator,
              write = write,
              partition = Some(
                Partition(
                  sampling = partitionSamplingOpt,
                  attributes = partitionColumnsOpt
                )
              ),
              sink = sinkColumnsOpt.map(Sink.fromType)
            )
            val mergeOptions: Option[MergeOptions] = (deltaColOpt, identityKeysOpt) match {
              case (Some(deltaCol), Some(identityKeys)) =>
                Some(
                  MergeOptions(
                    key = identityKeys.split(",").toList.map(_.trim),
                    timestamp = Some(deltaCol)
                  )
                )
              case (None, Some(identityKeys)) =>
                Some(
                  MergeOptions(key = identityKeys.split(",").toList.map(_.trim))
                )
              case _ => None
            }
            Some(
              Schema(
                name,
                pattern,
                attributes = Nil,
                Some(metaData),
                mergeOptions,
                comment,
                None,
                None
              )
            )
          }
          case _ => None
        }
      }
      .toList
  }

  private def buildSchemas(settings: Settings): List[Schema] = {
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
                .flatMap(formatter.formatCellValue)
              val renameOpt = Option(row.getCell(1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
              val semTypeOpt = Option(row.getCell(2, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
              val required = Option(row.getCell(3, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
                .forall(_.toBoolean)
              val privacy = Option(row.getCell(4, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
                .map(PrivacyLevel.ForSettings(settings).fromString)
              val metricType = Option(row.getCell(5, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
                .map(MetricType.fromString)
              val defaultOpt = Option(row.getCell(6, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
              val scriptOpt = Option(row.getCell(7, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)
              val commentOpt = Option(row.getCell(8, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                .flatMap(formatter.formatCellValue)

              val positionOpt = schema.metadata.flatMap(_.format) match {
                case Some(Format.POSITION) => {
                  val positionStart =
                    Option(row.getCell(9, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                      .flatMap(formatter.formatCellValue)
                      .map(_.toInt) match {
                      case Some(v) => v - 1
                      case _       => 0
                    }
                  val positionEnd =
                    Option(row.getCell(10, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                      .flatMap(formatter.formatCellValue)
                      .map(_.toInt) match {
                      case Some(v) => v - 1
                      case _       => 0
                    }
                  Some(Position(positionStart, positionEnd))
                }
                case _ => None
              }
              val attributeTrim =
                Option(row.getCell(11, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL))
                  .flatMap(formatter.formatCellValue)
                  .map(Trim.fromString)

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
                      trim = attributeTrim,
                      position = positionOpt,
                      default = defaultOpt,
                      script = scriptOpt,
                      tags = None,
                      attributes = None
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

  /**
    * Returns the Domain corresponding to the parsed spreadsheet
    * @param settings
    * @return an Option of Domain
    */
  def getDomain()(implicit settings: Settings): Option[Domain] = {
    val completeSchemas = buildSchemas(settings).filter(_.attributes.nonEmpty)
    domain.map(_.copy(schemas = completeSchemas))
  }
}
