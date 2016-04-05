package dao

import scala.io.Source

case class Coordinate(latitude: Double, longitude: Double)
case class Suggestion(name: String, latitude: String, longitude: String, score: Double)

object Dao {

    def using[A <: AutoCloseable, B](ac: A)(block: A => B) = {
        try {
            block(ac)
        } finally {
            ac.close()
        }
    }

    type Row = Map[String, String]
    type ScoredRow = (Double, Row)

    private lazy val rawData = Source.fromFile("data/cities_canada-usa.tsv")
                      .getLines
                      .map(_.split("\\t").toSeq)
                      .toSeq
    lazy val columnNameByIndex = rawData.head
    lazy val columnNameToIndex = rawData.head.zipWithIndex.toMap

    lazy val rowData = rawData.drop(1)
                              // make rows associative instead of index based
                              .map{ row => row.zipWithIndex.map(z => columnNameByIndex(z._2) -> z._1).toMap }

    def mapSuggestion(t: ScoredRow) = {
        val row = t._2
        Suggestion(row("name"), row("lat"), row("long"), t._1)
    }
}

class Dao {
    import Dao._

    def selectByNameStart(namePrefix: String, coordinates: Option[Coordinate] = None) = {
        val map = (r: Seq[ScoredRow]) => r.map(mapSuggestion)

        val scoreByName = (results: Seq[Row]) => {
            val filtered = results.filter(_("name").toLowerCase.startsWith(namePrefix))
            filtered
                   .sortBy(_("name"))
                   .zipWithIndex
                   .map { case (result, index) =>
                val score = 1 - (index / (filtered.length-1).toDouble)
                score -> result
            }
        }

        val scoreByCoordinates = identity[Seq[ScoredRow]] _ // TODO: score

        val sort = (r: Seq[Suggestion]) => r.sortWith( (a, b) => a.score > b.score )

        val search = scoreByName andThen scoreByCoordinates andThen map andThen sort

        search(rowData)
    }
}
