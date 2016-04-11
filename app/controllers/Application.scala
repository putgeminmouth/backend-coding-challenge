package controllers

import javax.inject.Inject

import dao.{Suggestion, SuggestionDao}
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success, Try}

object Application {
    def response(status: Boolean, json: Option[JsObject] = None): JsValue =
        Json.obj("status" -> JsBoolean(status)) ++ json.getOrElse(Json.obj())
    def success(json: JsObject): JsValue=
        response(true, Some(json))
    def failure(message: String): JsValue =
        response(false, Some(Json.obj("message" -> message)))
}

class Application @Inject() (dao: SuggestionDao) extends Controller {
    import Application._

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
                Ok(success(Json.obj("suggestions" -> found)))
            case Left(error) =>
                error(failure(error.header.status.toString))
        }
    }
}
