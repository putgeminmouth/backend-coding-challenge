import java.sql.SQLException
import java.util.concurrent.atomic.AtomicReference

import dao.Suggestion
import org.scalactic.Prettifier
import org.scalatest._
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatestplus.play._
import org.specs2.execute.{Result, AsResult}
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.ws.{WSResponse, WS}
import play.api.mvc.Results
import play.api.test.{Helpers, WithServer}
import scripts.db.ImportDao
import util.db.usingNewConnection
import util.pattern.using

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

trait WithTestStatus extends SuiteMixin { self: Suite =>
    // BeforeAndAfter uses atomic/volatile, so we do too, even though
    // this probably doesn't make sense when parallellized
    val lastTestStatus = new AtomicReference[Option[Status]](None)

    abstract protected override def runTest(testName: String, args: Args): Status = {
        val status = super.runTest(testName, args)
        lastTestStatus.set(Some(status))
        status
    }
    abstract override def run(testName: Option[String], args: Args): Status = {
        val status = super.run(testName, args)
        lastTestStatus.set(Some(status))
        status
    }
}

// These are NOT parallelizable
class FuncSpec extends PlaySpec
    with Results
    with WithTestStatus
    with BeforeAndAfter
{
    implicit val suggestionFromJson = Json.reads[Suggestion]

    var port: Int = _
    var lastWSResponse: Option[WSResponse] = None

    def baseUrl = s"http://localhost:$port/suggestions"

    def url(name: String, latitude: Option[String], longitude: Option[String]) = {
        val paramStr = (latitude.map(l => s"&latitude=$l") ++
                        longitude.map(l => s"&longitude=$l")).flatten.mkString
        s"${baseUrl}?q=${name}&${paramStr}"
    }

    def url(name: String, latitude: String, longitude: String): String =
        url(name, Some(latitude), Some(longitude))

    def url(name: String, latitude: BigDecimal, longitude: BigDecimal): String =
        url(name, latitude.toString, longitude.toString)

    def url(name: String): String =
        url(name, None, None)

    def request(url: String) = {
        import play.api.Play.current
        Logger.debug(s"Request to $url")
        val response = Await.result(WS.url(url).get(), 10 seconds)
        lastWSResponse = Some(response)
        response
    }

    before {
        lastWSResponse = None
        port = Helpers.testServerPort

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

    after {
        lastTestStatus.get() match {
            case Some(FailedStatus) =>
                // for some reason using Logger doesn't output anything here
                lastWSResponse.foreach(r => println(s"Response body:\n${r.body}\n"))
            case _ =>
        }
    }

    def assertOrder(actual: Seq[Suggestion], expectedNames: Seq[String]) = {
        val actualNames = actual.map(_.name)
        actualNames must equal(expectedNames)
    }

    "bad request" should {
        "be returned when name parameter is not specified" in new WithServer(port = port) {
            val response = request(baseUrl)

            response.status must equal(BadRequest.header.status)
        }

        "be returned when latitude is not specified" in new WithServer(port = port) {
            val response = request(url("name", "not a number", "5"))

            response.status must equal(BadRequest.header.status)
        }

        "be returned when longitude is not specified" in new WithServer(port = port) {
            val response = request(url("name", "5", "not a number"))

            response.status must equal(BadRequest.header.status)
        }
    }
    "name-only suggestion edge cases" should {
        "return empty results when there is no match" in new WithServer(port = port) {
            val nonExistantName = "Cair Paravel"

            val response = request(url(nonExistantName))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            sugs must equal(Seq.empty[Suggestion])
        }
    }

    "name-only suggestion ranking" should {
        "return improve match with more characters set 1" in new WithServer(port = port) {
            val name = "m"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            assertOrder(sugs, Seq(
                "Montréal, Quebec, Canada",
                "Montréal-Ouest, Quebec, Canada",
                "Montrose, Virginia, United States",
                "Montrose, Colorado, United States",
                "Monument, Denver, United States"
            ))
        }
        "return improve match with more characters set 2" in new WithServer(port = port) {
            val name = "mont"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            assertOrder(sugs, Seq(
                "Montréal, Quebec, Canada",
                "Montréal-Ouest, Quebec, Canada",
                "Montrose, Virginia, United States",
                "Montrose, Colorado, United States"
            ))
        }
        "return improve match with more characters set 3" in new WithServer(port = port) {
            val name = "Montreal"

            val response = request(url(name))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            assertOrder(sugs, Seq(
                "Montréal, Quebec, Canada",
                "Montréal-Ouest, Quebec, Canada"
            ))
        }
    }

    "name and coordinate suggestion edge cases" should {
        "return empty results when there is no match" in new WithServer(port = port) {
            val nonExistantName = "Cair Paravel"

            val response = request(url(nonExistantName, BigDecimal(0), BigDecimal(0)))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            sugs must equal(Seq.empty[Suggestion])
        }
    }

    "name and coordinate suggestion ranking" should {

        "disambiguate similar names by specifying more precise coordinates" in new WithServer(port = port) {
            val name = "Montré"

            /* set 1 */ {
                val latitude = BigDecimal(45)
                val longitude = BigDecimal(-73)

                val response = request(url(name, latitude, longitude))

                response.status must equal(Ok.header.status)

                val sugs = Json.parse(response.body).as[Seq[Suggestion]]

                assertOrder(sugs, Seq(
                    "Montréal, Quebec, Canada",
                    "Montréal-Ouest, Quebec, Canada"
                ))
            }

            /* set 2 */ {
                val latitude = BigDecimal(45.45286)
                val longitude = BigDecimal(-73.64918)

                val response = request(url(name, latitude, longitude))

                response.status must equal(Ok.header.status)

                val sugs = Json.parse(response.body).as[Seq[Suggestion]]

                assertOrder(sugs, Seq(
                    "Montréal-Ouest, Quebec, Canada",
                    "Montréal, Quebec, Canada"
                ))
            }
        }

        "disambiguate dissimilar names by specifying very precise coordinates" in new WithServer(port = port) {
            val name = "m"
            val latitude = BigDecimal(39.09166)
            val longitude = BigDecimal(-104.87276)

            val response = request(url(name, latitude, longitude))

            response.status must equal(Ok.header.status)

            val sugs = Json.parse(response.body).as[Seq[Suggestion]]

            assertOrder(sugs, Seq(
                "Monument, Denver, United States",
                "Montrose, Colorado, United States",
                "Montrose, Virginia, United States",
                "Montréal-Ouest, Quebec, Canada",
                "Montréal, Quebec, Canada"
            ))
        }
    }
}
