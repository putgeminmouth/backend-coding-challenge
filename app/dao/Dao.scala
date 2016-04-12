package dao

import java.sql.ResultSet
import javax.inject.{Inject, Named}

import play.api.Logger
import play.api.db.Database
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
    def selectByName(name: String, limit: Option[Int]): Seq[Suggestion]
    def selectByNameWithCoordinates(name: String, latitude: BigDecimal, longitude: BigDecimal, limit: Option[Int]): Seq[Suggestion]
}

class PrefixPostgresSuggestionDao @Inject()
    (@Named("dao.hardlimit") hardlimit: Int, db: Database)
    extends SuggestionDao {
    import Dao._

    private def applyScore(max: Double)(suggestion: Suggestion) =
        suggestion.copy(score = scoreNormalizedAgainstMaxValue(max)(suggestion.score))

    def selectByName(name: String, requestedLimit: Option[Int]): Seq[Suggestion] = {
        val like = s"${escapeLike(normalize(name))}%"
        val limit = Math.min(hardlimit, requestedLimit.getOrElse(hardlimit))

        db.withConnection { conn =>
            import GeonameTable._
            using(conn.prepareStatement(s"""
                  | SELECT *, ROW_NUMBER() OVER (order by name asc, division asc, country asc) as score
                  | FROM $tableName g
                  | WHERE normalized LIKE ?
                  | ORDER BY score
                  | LIMIT ?
                  |""".stripMargin)) { stmt =>
                stmt.setString(1, like)
                stmt.setInt(2, limit)

                Logger.debug(util.db.toDebugString(stmt))

                using(stmt.executeQuery()) { rs =>
                    val suggestions = mapResultSet(rs)(mapSuggestion)
                    suggestions
                        .map(applyScore(suggestions.length))

                }
            }
        }
    }

    def selectByNameWithCoordinates(name: String, latitude: BigDecimal, longitude: BigDecimal, requestedLimit: Option[Int]): Seq[Suggestion] = {
        val like = s"${escapeLike(normalize(name))}%"
        val limit = Math.min(hardlimit, requestedLimit.getOrElse(hardlimit))

        db.withConnection { conn =>
            import GeonameTable._

            using(conn.prepareStatement(s"""
                  | SELECT *, earth_distance(ll_to_earth(CAST(? as float8), CAST(? as float8)),
                  |                          ll_to_earth(CAST(latitude as float8), CAST(longitude as float8))) as score
                  | FROM $tableName g
                  | WHERE normalized LIKE ?
                  | ORDER BY score, name asc, division asc, country asc
                  | LIMIT ?
                  |""".stripMargin)) { stmt =>
                stmt.setBigDecimal(1, latitude.bigDecimal)
                stmt.setBigDecimal(2, longitude.bigDecimal)
                stmt.setString(3, like)
                stmt.setInt(4, limit)

                Logger.debug(util.db.toDebugString(stmt))

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

class SimilarityPostgresSuggestionDao @Inject()
    (@Named("dao.hardlimit") hardlimit: Int,
     @Named("similarity.nameWeight") nameWeight: Double,
     @Named("similarity.distanceWeight") distanceWeight: Double,
     @Named("similarity.distanceScale") distanceScale: Double,
     db: Database)
    extends SuggestionDao {
    import Dao._

    require(distanceScale != 0)
    require(nameWeight + distanceWeight == 1)

    def selectByName(name: String, requestedLimit: Option[Int]): Seq[Suggestion] = {
        val normalized = normalize(name)
        val limit = Math.min(hardlimit, requestedLimit.getOrElse(hardlimit))

        db.withConnection { conn =>
            import GeonameTable._
            using(conn.prepareStatement(s"""
                  | SELECT *, similarity(normalized, ?) as score
                  | FROM $tableName g
                  | ORDER BY score desc, name asc, division asc, country asc
                  | LIMIT ?
                  |""".stripMargin)) { stmt =>
                stmt.setString(1, normalized)
                stmt.setInt(2, limit)

                Logger.debug(util.db.toDebugString(stmt))

                using(stmt.executeQuery()) { rs =>
                    mapResultSet(rs)(mapSuggestion)
                }
            }
        }
    }

    def selectByNameWithCoordinates(name: String, latitude: BigDecimal, longitude: BigDecimal, requestedLimit: Option[Int]): Seq[Suggestion] = {
        val normalized = normalize(name)
        val limit = Math.min(hardlimit, requestedLimit.getOrElse(hardlimit))

        db.withConnection { conn =>
            import GeonameTable._

            using(conn.prepareStatement(s"""
                  | SELECT *,
                  |        similarity(normalized, ?)*$nameWeight + (
                  |            1 - LEAST(earth_distance(ll_to_earth(CAST(? as float8), CAST(? as float8)),
                  |                                     ll_to_earth(CAST(latitude as float8), CAST(longitude as float8))
                  |                     ) / $distanceScale,
                  |                1)
                  |        )*$distanceWeight as score
                  |
                  | FROM $tableName g
                  | ORDER BY score desc, name asc, division asc, country asc
                  | LIMIT ?
                  |""".stripMargin)) { stmt =>
                stmt.setString(1, normalized)
                stmt.setBigDecimal(2, latitude.bigDecimal)
                stmt.setBigDecimal(3, longitude.bigDecimal)
                stmt.setInt(4, limit)

                Logger.debug(util.db.toDebugString(stmt))

                using(stmt.executeQuery()) { rs =>
                    mapResultSet(rs)(mapSuggestion)
                }
            }
        }
    }
}
