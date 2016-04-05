package controllers

import dao.Dao
import play.api.libs.json._
import play.api.mvc._

object Application extends Controller {
    val dao = new Dao // TODO: dependency injection much?

    def index = Action {
        Ok(views.html.index())
    }

    def suggestions(namePrefix: String) = Action { request =>
        val found = dao.selectByNameStart(namePrefix)
        Ok(Json.toJson(found))
    }
}
