package controllers

import javax.inject.Inject

import dao.{Suggestion, SuggestionDao}
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success, Try}

class Application @Inject() (dao: SuggestionDao) extends Controller {
    // TODO: currently converts BigDecimal to float with possible rounding error
    implicit val suggestionToJson = Json.writes[Suggestion]

    def index = Action {
        Ok(views.html.index())
    }

    def suggestions(nameParam: String,
                    latitudeParam: Option[String],
                    longitudeParam: Option[String],
                    limit: Option[Int]) = Action { request =>

        def parseBigDecimal(optBd: Option[String]): Option[Try[BigDecimal]] =
            optBd.map(_.trim).map(o => Try(BigDecimal(o)))

        val result = (nameParam, parseBigDecimal(latitudeParam), parseBigDecimal(longitudeParam)) match {
            case (name, Some(Success(latitude)), Some(Success(longitude))) if name.trim.nonEmpty =>
                Right(dao.selectByNameWithCoordinates(name, latitude, longitude, limit))

            case (name, None, None) if name.trim.nonEmpty =>
                Right(dao.selectByName(nameParam, limit))

            case _ =>
                Left(BadRequest)
        }

        result match {
            case Right(found) =>
                Ok(Json.toJson(found))
            case Left(error) =>
                error
        }
    }
}
