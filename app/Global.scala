import controllers.Application
import play.api.http.HttpErrorHandler
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future

object Global extends WithFilters() {

}

class ErrorHandler extends HttpErrorHandler {
    import Application._

    override def onClientError(request: RequestHeader, statusCode: Int, message: String = ""): Future[Result] = {
        val status = Status(statusCode)
        val msg = if (message.nonEmpty) message else status.header.status.toString
        Future.successful(status(failure(msg)))
    }

    override def onServerError(request: RequestHeader, exception: Throwable) = {
        val msg = s"${exception.getClass.getSimpleName}: ${exception.getMessage}"
        Future.successful(InternalServerError(failure(msg)))
    }
}