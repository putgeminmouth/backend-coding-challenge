package controllers

import play.api.libs.json._
import play.api.mvc._

object Application extends Controller {

    def index = Action {
        Ok(views.html.index())
    }

    def suggestions = Action { request =>
        Ok(Json.toJson(Map.empty[String, String]))
    }
}
