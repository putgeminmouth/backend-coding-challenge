package controllers

import dao.{PostgresSuggestionDao, Suggestion, Dao}
import play.api.libs.json._
import play.api.mvc._


object Application extends Controller {
    implicit val suggestionToJson = Json.writes[Suggestion]

    val dao = new PostgresSuggestionDao // TODO: dependency injection much?

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
