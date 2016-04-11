import controllers.Application
import dao.{Suggestion, SuggestionDao}
import org.mockito.Matchers._
import org.mockito.Matchers.{eq => eqmatch}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.duration._

class ApplicationSpec extends PlaySpec
    with Results
    with BeforeAndAfter
    with MockitoSugar
{
    var dao: SuggestionDao = _

    var app: Application = _


    before {
        dao = mock[SuggestionDao]

        app = new GuiceApplicationBuilder()
                .overrides(bind(classOf[SuggestionDao]).toInstance(dao))
                .build
                .injector.instanceOf(classOf[Application])
    }

    after {
        verifyNoMoreInteractions(dao)
    }

    "suggestions" should {
        "call the right Dao when no coordiates are given" in {
            val name = "Montreal"

            when(dao.selectByName(anyString(), any[Option[Int]])) thenReturn Seq.empty[Suggestion]

            val result = app.suggestions(name, None, None, None)(FakeRequest())
            Await.result(result, 0 seconds)

            verify(dao, times(1)).selectByName(eqmatch(name), any[Option[Int]])
        }
        "call the right Dao when both coordinates are given" in {
            val name = "Toronto"
            val latitudeStr = "12.34"
            val latitude    = BigDecimal(latitudeStr)
            val longitudeStr = "-43.21"
            val longitude   = BigDecimal(longitudeStr)

            when(dao.selectByNameWithCoordinates(anyString(), any[BigDecimal], any[BigDecimal], any[Option[Int]])) thenReturn Seq.empty[Suggestion]

            val result = app.suggestions(name, Some(latitudeStr), Some(longitudeStr), None)(FakeRequest())
            Await.result(result, 0 seconds)

            verify(dao, times(1)).selectByNameWithCoordinates(eqmatch(name), eqmatch(latitude), eqmatch(longitude), any[Option[Int]])
        }
        "return an error if a coordinate is NaN" in {
            when(dao.selectByName(anyString(), any[Option[Int]])) thenReturn Seq.empty[Suggestion]

            val validNumber = "6"
            Seq(
                "",
                " ",
                "not a number"
            ).foreach { invalidNumber =>
                status(
                    app.suggestions("hello", Some(invalidNumber), Some(validNumber), None)(FakeRequest())
                ) must equal(BAD_REQUEST)

                status(
                    app.suggestions("hello", Some(validNumber), Some(invalidNumber), None)(FakeRequest())
                ) must equal(BAD_REQUEST)

                status(
                    app.suggestions("hello", Some(invalidNumber), Some(invalidNumber), None)(FakeRequest())
                ) must equal(BAD_REQUEST)
            }
        }
        "return an error if only one coordinate is given" in {
            when(dao.selectByName(anyString(), any[Option[Int]])) thenReturn Seq.empty[Suggestion]

            val result = app.suggestions("hello", Some("1.2"), None, None)(FakeRequest())
            status(result) must equal(BAD_REQUEST)
        }
        "return json results in the right format (i.e. json matches the spec)" in {
            val suggestions = Seq(
                Suggestion("Montreal", BigDecimal(1), BigDecimal(2), 1),
                Suggestion("Toronto", BigDecimal(3), BigDecimal(4), 0)
            )

            when(dao.selectByName(anyString(), any[Option[Int]])) thenReturn suggestions

            val result = app.suggestions("hi", None, None, None)(FakeRequest())
            status(result) must equal(OK)
            val values = contentAsJson(result).as[JsArray].value

            values must equal(Seq(
                Json.obj(
                    "name" -> "Montreal",
                    "latitude" -> 1,
                    "longitude" -> 2,
                    "score" -> 1
                ),
                Json.obj(
                    "name" -> "Toronto",
                    "latitude" -> 3,
                    "longitude" -> 4,
                    "score" -> 0
                )
            ))
            verify(dao, times(1)).selectByName(anyString(), any[Option[Int]])
        }
    }
}
