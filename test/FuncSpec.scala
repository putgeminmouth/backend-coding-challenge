import java.sql.SQLException
import java.util.concurrent.atomic.AtomicReference

import controllers.Application
import dao.{SuggestionDao, Suggestion}
import org.scalatest._
import org.scalatestplus.play._
import org.specs2.execute.{Result, AsResult}
import play.api.{Application, Logger}
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, JsArray, Json}
import play.api.libs.ws.{WSClient, WS, WSResponse}
import play.api.mvc.Results
import play.api.test.{Helpers, WithServer}
import scripts.db.ImportDao
import util.db.usingNewConnection
import util.pattern.using

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

// These are NOT parallelizable
class FuncSpec extends PlaySpec
    with Results
    with BeforeAndAfter
{
    implicit val suggestionFromJson = Json.reads[Suggestion]

    class Test(val appBuilder: GuiceApplicationBuilder = GuiceApplicationBuilder())
        extends WithServer(app=appBuilder.build()) {
        var wsClient: WSClient = _

        override def around[T: AsResult](t: => T): Result = {
            inject()

            before()

            val ret = super.around(t)

            after()

            ret
        }

        def inject() {
            wsClient = app.injector.instanceOf[WSClient]
        }

        def before() {
            try {
                usingNewConnection { conn =>
                    import dao.GeonameTable._

                    conn.setAutoCommit(true)

                    using(conn.prepareStatement(s"TRUNCATE TABLE $tableName")) {
                        _.execute()
                    }
                }

                ImportDao.insert(Seq(
                    ImportDao.City(6077243, "Montréal", "montreal",             BigDecimal("45.50884"), BigDecimal("-73.58781"),  "Canada",        "Quebec"),
                    ImportDao.City(6077265, "Montréal-Ouest", "montreal-ouest", BigDecimal("45.45286"), BigDecimal("-73.64918"),  "Canada",        "Quebec"),
                    ImportDao.City(4773747, "Montrose", "montrose",             BigDecimal("38.47832"), BigDecimal("-77.37831"),  "United States", "Virginia"),
                    ImportDao.City(5431710, "Montrose", "montrose",             BigDecimal("45.50884"), BigDecimal("-107.87617"), "United States", "Colorado"),
                    ImportDao.City(5431740, "Monument", "monument",             BigDecimal("39.09166"), BigDecimal("-104.87276"), "United States", "Denver")
                ))
            } catch {
                case e: SQLException =>
                    e.iterator().asScala.foreach(Logger.error("", _))
            }
        }

        def after() {
            Try(wsClient.close())
        }

        def baseUrl = s"http://localhost:$port/suggestions"

        def url(name: String, latitude: Option[String], longitude: Option[String]) = {
            val paramStr = (latitude.map(l => s"&latitude=$l") ++
                longitude.map(l => s"&longitude=$l")).flatten.mkString
            s"${baseUrl}?q=${name}${paramStr}"
        }

        def url(name: String, latitude: String, longitude: String): String =
            url(name, Some(latitude), Some(longitude))

        def url(name: String, latitude: BigDecimal, longitude: BigDecimal): String =
            url(name, latitude.toString, longitude.toString)

        def url(name: String): String =
            url(name, None, None)

        def request(url: String) = {
            Logger.debug(s"Request to $url")
            val response = Await.result(wsClient.url(url).get(), 10 seconds)
            Logger.debug(s"Response body:\n${response.body}")
            response
        }

        def responseSuggestions(response: WSResponse) =
            (Json.parse(response.body) \ "suggestions")
                .as[JsArray]
                .as[Seq[Suggestion]]
    }

    def assertOrder(actual: Seq[Suggestion], expectedNames: Seq[String]) = {
        val actualNames = actual.map(_.name)
        actualNames must equal(expectedNames)
    }

    "bad request" should {
        "be returned when name parameter is not specified" in new Test() {
            val response = request(baseUrl)

            response.status must equal(BadRequest.header.status)
        }

        "be returned when latitude is not specified" in new Test() {
            val response = request(url("name", "not a number", "5"))

            response.status must equal(BadRequest.header.status)
        }

        "be returned when longitude is not specified" in new Test() {
            val response = request(url("name", "5", "not a number"))

            response.status must equal(BadRequest.header.status)
        }
    }
    "limits" should {
        "be respected" in new Test() {
            /* 1 */ {
                val response = request(s"${url("m")}&limit=1")
                val sugs = responseSuggestions(response)
                sugs.length must equal(1)
            }

            /* 2 */ {
                val response = request(s"${url("m")}&limit=2")
                val sugs = responseSuggestions(response)
                sugs.length must equal(2)
            }
        }
    }
    "name-only suggestion ranking" should {
        "return improve match with more characters set 1" in new Test() {
            val name = "m"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = responseSuggestions(response)

            assertOrder(sugs, Seq(
                "Montrose, Colorado, United States",
                "Montrose, Virginia, United States",
                "Montréal, Quebec, Canada",
                "Monument, Denver, United States",
                "Montréal-Ouest, Quebec, Canada"
            ))
        }
        "return improve match with more characters set 2" in new Test() {
            val name = "montrea"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = responseSuggestions(response)

            assertOrder(sugs, Seq(
                "Montréal, Quebec, Canada",
                "Montréal-Ouest, Quebec, Canada",
                "Montrose, Colorado, United States",
                "Montrose, Virginia, United States",
                "Monument, Denver, United States"
            ))
        }
        "return improve match with more characters set 3" in new Test() {
            val name = "Montreal Ouest"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = responseSuggestions(response)

            assertOrder(sugs, Seq(
                "Montréal-Ouest, Quebec, Canada",
                "Montréal, Quebec, Canada",
                "Montrose, Colorado, United States",
                "Montrose, Virginia, United States",
                "Monument, Denver, United States"
            ))
        }
    }

    "name and coordinate suggestion ranking" should {

        "disambiguate similar names by specifying more precise coordinates" in new Test() {
            val name = "Montré"

            /* set 1 */ {
                val latitude = BigDecimal(45.5)
                val longitude = BigDecimal(-73.6)

                val response = request(url(name, latitude, longitude))

                response.status must equal(Ok.header.status)

                val sugs = responseSuggestions(response)

                assertOrder(sugs, Seq(
                    "Montréal, Quebec, Canada",
                    "Montréal-Ouest, Quebec, Canada",
                    "Montrose, Colorado, United States",
                    "Montrose, Virginia, United States",
                    "Monument, Denver, United States"
                ))
            }

            /* set 2 */ {
                val latitude = BigDecimal(45.45286)
                val longitude = BigDecimal(-73.64918)

                val response = request(url(name, latitude, longitude))

                response.status must equal(Ok.header.status)

                val sugs = responseSuggestions(response)

                assertOrder(sugs, Seq(
                    "Montréal-Ouest, Quebec, Canada",
                    "Montréal, Quebec, Canada",
                    "Montrose, Colorado, United States",
                    "Montrose, Virginia, United States",
                    "Monument, Denver, United States"
                ))
            }
        }

        "disambiguate dissimilar names by specifying very precise coordinates" in new Test() {
            val name = "m"
            val latitude = BigDecimal(39.09166)
            val longitude = BigDecimal(-104.87276)

            val response = request(url(name, latitude, longitude))

            response.status must equal(Ok.header.status)

            val sugs = responseSuggestions(response)

            assertOrder(sugs, Seq(
                "Monument, Denver, United States",
                "Montrose, Colorado, United States",
                "Montrose, Virginia, United States",
                "Montréal, Quebec, Canada",
                "Montréal-Ouest, Quebec, Canada"
            ))
        }
    }
}
