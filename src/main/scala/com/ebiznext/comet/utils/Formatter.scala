package com.ebiznext.comet.utils

object Formatter extends Formatter

trait Formatter {

  implicit def RichFormatter(str: String): Object {
    def richFormat(replacement: Map[String, String]): String
  } = new {
    def richFormat(replacement: Map[String, String]): String =
      (str /: replacement) { (res, entry) =>
        res.replaceAll("\\{\\{%s\\}\\}".format(entry._1), entry._2.toString)
      }
  }
}
