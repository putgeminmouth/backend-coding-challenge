package dao

import scala.io.Source

case class Suggestion(name: String, latitude: String, longitude: String, score: Int)

object Dao {

    def using[A <: AutoCloseable, B](ac: A)(block: A => B) = {
        try {
            block(ac)
        } finally {
            ac.close()
        }
    }

    private lazy val rawData = Source.fromFile("data/cities_canada-usa.tsv")
                      .getLines
                      .map(_.split("\\t").toSeq)
                      .toSeq
                      .map( row => row.map( cell => cell.toLowerCase ) ) // TODO: temp simplification
    lazy val columnNameToIndex = rawData.head.zipWithIndex.toMap

    lazy val rowData = rawData.drop(1)
    def col(name: String)(implicit row: Seq[String]) = columnNameToIndex.get(name).map(row).get

    def mapSuggestion(row: Seq[String]) = {
        implicit val r = row
        Suggestion(col("name"), col("lat"), col("long"), 0)
    }
}

class Dao {
    import Dao._
    def selectByNameStart(namePrefix: String) = {
        rowData.filter{ implicit row =>
            col("name").startsWith(namePrefix)
        }.map(mapSuggestion)
        // TODO: score
         .sortWith( (a, b) => b.score > a.score )
    }
}