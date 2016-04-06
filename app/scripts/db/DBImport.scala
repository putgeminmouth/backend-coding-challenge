package scripts.db

import java.sql.{Connection, DriverManager, PreparedStatement}

import util.pattern.using
import util.text.normalize

import scala.io.Source

object ImportDao {
    case class Row(id: Int, name: String, ascii: String, normalized: String, latitude: BigDecimal, longitude: BigDecimal, admin1: String, admin2: String, admin3: String, admin4: String)

    def usingConnection(block: Connection => Unit) {
        val conn = DriverManager.getConnection(sys.env("DATABASE_URL"))
        using(conn) {
            // TODO: are we okay with partial loads?
            conn.setAutoCommit(true)

            block
        }
    }

    def mapRow(row: Map[String, String]) =
        Row(row("id").toInt,
            row("name"), row("ascii"), normalize(row("ascii")),
            BigDecimal(row("lat")), BigDecimal(row("long")),
            row("admin1"), row("admin2"), row("admin3"), row("admin4"))

    def insert(rows: Seq[Row]) {
        def addBatch(stmt: PreparedStatement) = {
            def extract(row: Row) {
                stmt.setInt(1, row.id)
                stmt.setString(2, row.name)
                stmt.setString(3, row.ascii)
                stmt.setString(4, row.normalized)
                stmt.setBigDecimal(5, row.latitude.bigDecimal)
                stmt.setBigDecimal(6, row.longitude.bigDecimal)
                stmt.setString(7, row.admin1)
                stmt.setString(8, row.admin2)
                stmt.setString(9, row.admin3)
                stmt.setString(10, row.admin4)

                stmt.addBatch()
            }
            extract _
        }

        usingConnection { conn =>

            using(conn.prepareStatement("""
             INSERT INTO geoname
             (id, name, ascii, normalized, latitude, longitude, admin1, admin2, admin3, admin4)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.stripMargin)) { stmt =>

                rows.grouped(1000).foreach { batch =>

                    batch.foreach(addBatch(stmt))

                    val insertCount = stmt.executeBatch().sum
                    if (insertCount != batch.size) {
                        // TODO:
                        //       more predantic handling of info in the returned arrray
                        //       more information about what failed in the batch
                        //       plus partial retry, etc...
                        sys.error(s"Batch insert failed: inserted $insertCount.length / ${batch.size}")
                    }
                }
            }
        }
    }
}

object DBImport {
    def loadFileData() = {
        Source.fromFile("data/cities_canada-usa.tsv")
              .getLines
    }
    val parseRawData = (lines: Iterator[String]) => {
        val rawData = lines
            .map(_.split("\\t").toSeq)
            .toSeq
        val columnNameByIndex = rawData.head

        val rows = rawData.drop(1)
            // make rows associative instead of index based
            .map{ row => row.zipWithIndex.map(z => columnNameByIndex(z._2) -> z._1).toMap }
        rows
    }
    val doImport = (rawRows: Seq[Map[String, String]]) => {
        val mappedRows = rawRows.map(ImportDao.mapRow)

        ImportDao.insert(mappedRows)
    }
    def main(args: Array[String]) {
        (parseRawData andThen doImport)(loadFileData())
    }
}
