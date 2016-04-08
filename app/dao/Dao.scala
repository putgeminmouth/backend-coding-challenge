package dao

import java.sql.ResultSet

import play.api.db.DB
import util.pattern.using
import util.text.normalize

case class Suggestion(name: String,
                      latitude: BigDecimal,
                      longitude: BigDecimal,
                      score: Double)

object GeonameTable {
    val tableName = "geoname"
}

object Dao {

    def mapSuggestion(rs: ResultSet) =
        Suggestion(
            s"${rs.getString("name")}, ${rs.getString("division")}, ${rs.getString("country")}",
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getDouble("score"))

    def mapResultSet[A](rs: ResultSet)(mapper: ResultSet => A) =
        Iterator.continually(rs)
                .takeWhile(_.next)
                .map(mapper)
                // ensure all rows processed before closing rs
                // also make things like getting length safe
                // OK since we always iterate over all results
                .toList


    // http://www.postgresql.org/docs/8.3/static/functions-matching.html
    val likeSpecialChars = Set('?', '_', '%')
    def escapeLike(like: String) =
        like.filterNot(likeSpecialChars)

    def scoreNormalizedAgainstMaxValue(max: Double) =
        (score: Double) => 1 - score / max
}

trait SuggestionDao {
    def selectByName(name: String): Seq[Suggestion]
    def selectByNameWithCoordinates(name: String, latitude: BigDecimal, longitude: BigDecimal): Seq[Suggestion]
}

class PostgresSuggestionDao extends SuggestionDao {
    import Dao._
    import play.api.Play.current

    private def applyScore(max: Double)(suggestion: Suggestion) =
        suggestion.copy(score = scoreNormalizedAgainstMaxValue(max)(suggestion.score))

    def selectByName(name: String): Seq[Suggestion] = {
        val like = s"${escapeLike(normalize(name))}%"

        DB.withConnection() { conn =>
            import GeonameTable._
            using(conn.prepareStatement(s"""
                  | SELECT *, ROW_NUMBER() OVER (order by normalized) as score
                  | FROM $tableName g
                  | WHERE normalized LIKE ?
                  | ORDER BY score
                  |""".stripMargin)) { stmt =>
                stmt.setString(1, like)

                using(stmt.executeQuery()) { rs =>
                    val suggestions = mapResultSet(rs)(mapSuggestion)
                    suggestions
                        .map(applyScore(suggestions.length))

                }
            }
        }
    }

    def selectByNameWithCoordinates(name: String, latitude: BigDecimal, longitude: BigDecimal): Seq[Suggestion] = {
        val like = s"${escapeLike(normalize(name))}%"

        DB.withConnection() { conn =>
            import GeonameTable._
            // technically we could avoid returning distance from the query
            // we could optimize by not taking SQRT since we are just comparing
            using(conn.prepareStatement(s"""
                  | SELECT *, SQRT((latitude - ?)^2 + (longitude - ?)^2) as score
                  | FROM $tableName g
                  | WHERE normalized LIKE ?
                  | ORDER BY score
                  |""".stripMargin)) { stmt =>
                stmt.setBigDecimal(1, latitude.bigDecimal)
                stmt.setBigDecimal(2, longitude.bigDecimal)
                stmt.setString(3, like)

                using(stmt.executeQuery()) { rs =>
                    val suggestions = mapResultSet(rs)(mapSuggestion)
                    val maxDistance = suggestions.foldLeft(0.0) { (acc, next) =>
                        if (next.score > acc) next.score else acc
                    }
                    suggestions
                        .map(applyScore(maxDistance))
                }
            }
        }
    }
}
