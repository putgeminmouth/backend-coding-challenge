package controllers

import javax.inject.Inject

import dao.{Suggestion, SuggestionDao}
import play.api.libs.json._
import play.api.mvc._

class Application @Inject() (dao: SuggestionDao) extends Controller {
    implicit val suggestionToJson = Json.writes[Suggestion]

    def index = Action {
        Ok(views.html.index())
    }

    def suggestions(name: String, latitude: Option[String], longitude: Option[String]) = Action { request =>
        val found = if (latitude.isDefined && longitude.isDefined) {
            dao.selectByNameWithCoordinates(name, BigDecimal(latitude.get), BigDecimal(longitude.get))
        } else {
            dao.selectByName(name)
        }
        Ok(Json.toJson(found))
    }
}
