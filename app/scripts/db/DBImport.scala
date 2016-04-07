package scripts.db

import java.sql.{Connection, DriverManager, PreparedStatement}

import util.pattern.using
import util.text.normalize

import scala.io.Source

object ImportDao {
    type CityRow = Map[String, String]
    type CountryCodeToName = Map[String, String]
    type DivisionToName = Map[String, String]

    case class City(id: Int, name: String, normalized: String, latitude: BigDecimal, longitude: BigDecimal, country: String, division: String)

    def usingConnection(block: Connection => Unit) {
        val conn = DriverManager.getConnection(sys.env("DATABASE_URL"))
        using(conn) {
            // TODO: are we okay with partial loads?
            conn.setAutoCommit(true)

            block
        }
    }

    def mapCity(countryCodeToName: CountryCodeToName,
                divisionToNameByCountry: Map[String, DivisionToName])
               (row: Map[String, String]) = {
        // TODO: nicer error and possibly tolerance when 2ndary lookups fail
        val countryCode = row("country")
        val countryName = countryCodeToName(countryCode)
        val divisionName = divisionToNameByCountry(countryCode)(row("admin1"))
        City(row("id").toInt,
            row("name"), normalize(row("ascii")),
            BigDecimal(row("lat")), BigDecimal(row("long")),
            countryName, divisionName)
    }

    def insert(rows: Seq[City]) {
        def addBatch(stmt: PreparedStatement) = {
            def extract(row: City) {
                stmt.setInt(1, row.id)
                stmt.setString(2, row.name)
                stmt.setString(3, row.normalized)
                stmt.setBigDecimal(4, row.latitude.bigDecimal)
                stmt.setBigDecimal(5, row.longitude.bigDecimal)
                stmt.setString(6, row.country)
                stmt.setString(7, row.division)

                stmt.addBatch()
            }
            extract _
        }

        usingConnection { conn =>

            using(conn.prepareStatement("""
             INSERT INTO geoname
             (id, name, normalized, latitude, longitude, country, division)
             VALUES (?, ?, ?, ?, ?, ?, ?)
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
    import ImportDao.{City, CityRow, CountryCodeToName, DivisionToName, mapCity}

    def indexedToAssociative(columnNameByIndex: Seq[String])(indexed: Seq[String]) =
        indexed.zipWithIndex.map(z => columnNameByIndex(z._2) -> z._1).toMap

    def loadCities() =
        Source.fromFile("data/cities_canada-usa.tsv")
              .getLines
              .toSeq
    def loadCountries() =
        Source.fromFile("data/countryInfo.txt")
              .getLines
              .toSeq
    def loadAdmin1() =
        Source.fromFile("data/admin1CodesASCII.txt")
              .getLines
              .toSeq

    val parseCities: (Seq[String]) => Seq[CityRow] = lines => {
        val rawData = lines
            .map(_.split("\\t").toSeq)

        val columnNameByIndex = rawData.head // assumes column names present

        val rows = rawData.drop(1)
            .map(indexedToAssociative(columnNameByIndex))
        rows.toSeq
    }

    val parseCountries: (Seq[String]) => CountryCodeToName = mixed => {
        val (comments, lines) = mixed
            .partition(_.startsWith("#"))
        val columnNameByIndex = comments
            .toSeq
            .last // assumes column names present
            .drop(1) // the #
            .split("\\t").toSeq

        val rows = lines
            .map(_.split("\\t").toSeq)
            .map(indexedToAssociative(columnNameByIndex))
            .map( row => row("ISO") -> row("Country") )
            .toMap
        rows
    }

    // many assumptions on data format here
    val parseAdmin1: (Seq[String]) => Map[String, DivisionToName] = lines => {
        val COUNTRY = 0
        val DIVISION = 1
        val NAME = 2

        val divisionRowToPair = (row: Array[String]) => row(DIVISION) -> row(NAME)
        val divisionRowsToMap = (divisionRowsToMap: Seq[Array[String]]) => divisionRowsToMap.map(divisionRowToPair).toMap

        val rows = lines
            .map(_.split("\\t").toSeq)
            .map(row => row(0).split('.') ++ row.drop(1)) // split up COUNTRY/DIVISION
            .groupBy(_(COUNTRY))
            .mapValues(divisionRowsToMap)
        rows
    }

    val doImport = (rawRows: Seq[Map[String, String]]) => {
        val countryCodeToName = parseCountries(loadCountries())
        val divisionToNameByCountry = parseAdmin1(loadAdmin1())

        val mapper = mapCity(countryCodeToName, divisionToNameByCountry) _
        val mappedRows: Seq[City] = rawRows.map(mapper)

        ImportDao.insert(mappedRows)
    }
    def main(args: Array[String]) {
        (parseCities andThen doImport)(loadCities())
    }
}
