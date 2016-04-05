package controllers

import dao.{Suggestion, Dao}
import play.api.libs.json._
import play.api.mvc._


object Application extends Controller {
    implicit val suggestionToJson = Json.writes[Suggestion]

    val dao = new Dao // TODO: dependency injection much?

    def index = Action {
        Ok(views.html.index())
    }

    def suggestions(namePrefix: String) = Action { request =>
        val found = dao.selectByNameStart(namePrefix)
        Ok(Json.toJson(found))
    }
}
