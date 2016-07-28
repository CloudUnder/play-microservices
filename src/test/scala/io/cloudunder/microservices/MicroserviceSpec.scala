package io.cloudunder.microservices

//import org.scalatest._
import org.scalatestplus.play._
import org.scalatest.Matchers._
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.libs.json.Json
import play.api.mvc.{Action, Results}
import play.api.test.Helpers._

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.EitherValues
import mockws.{MockWS, Route}

import scala.collection.Set
import scala.concurrent.duration.FiniteDuration


class MicroserviceSpec extends PlaySpec with Results with ScalaFutures with EitherValues {

	implicit val defaultPatience = PatienceConfig(timeout = Span(5000, Millis), interval = Span(50, Millis))

	val route1 = Route {
		//case (GET, "/test") => Action { Ok("test") }
		case (GET, "http://testservice:9999/test") => Action { Ok("success") }
		case (GET, "http://testservice:9999/create") => Action { Created }
		case (GET, "http://testservice:9999/json") => Action { Ok(Json.parse("""{"result": "OK"}""")) }
		case (GET, "http://testservice:9999/notfound") => Action { NotFound }
		case (GET, "http://testservice:9999/jsontoomany") => Action { TooManyRequests(Json.parse("""{"foo": "bar"}""")) }
		case (GET, "http://testservice:9999/jsonbad") => Action { BadRequest(Json.parse("""{"foo": "bar"}""")) }
		case (GET, "http://testservice:9999/jsonmad") => Action { BadRequest(Json.parse("""[1, 2, 3]""")) }
		case (GET, "http://testservice:9999/slow") => Action {
			Thread.sleep(10000)
			Ok("success")
		}
	}

	val config1 = Configuration(
		"microservices.services.test.url" -> "http://testservice:9999"
	)

	private class TestMicroservice(serviceName: String, configuration: Configuration, ws: WSClient) extends Microservice(serviceName, configuration, ws) {
		def __dataErrorCodes: Set[Int] = dataErrorCodes
		def __timeout: FiniteDuration = timeout
//		def __optServiceSecret = optServiceSecret
		def __optServiceURL: Option[String] = optServiceURL
//		def __defaultHeaders = defaultHeaders
		def __endpointURL(endpoint: String): String = endpointURL(endpoint)
		def __handleFutureResponse(futureResponse: Future[WSResponse]) = handleFutureResponse(futureResponse)
		def __asEither(futOptWSResponse: Future[Option[WSResponse]]) = asEither(futOptWSResponse)
		def __ws: WSClient = ws
		def getAsOpt(endpoint: String): Future[Option[WSResponse]] = {
			handleFutureResponse(ws.url(endpointURL(endpoint)).withRequestTimeout(timeout).get)
		}
		def getAsEither(endpoint: String): Future[Either[ServiceError, ServiceResponse] with Product with Serializable] = {
			asEither(handleFutureResponse(ws.url(endpointURL(endpoint)).withRequestTimeout(timeout).get))
		}
	}


	"Microservice#dataErrorCodes" should {
		"by default only contain 4xx status codes" in {
			val ws = MockWS(route1)

			new TestMicroservice("testservice", Configuration.empty, ws).__dataErrorCodes.foreach(code => code should (be >= 400 and be <= 499))

			ws.close
		}
	}

	"Microservice#timeout" should {
		"by default be between 50 and 60000ms" in {
			val ws = MockWS(route1)

			new TestMicroservice("testservice", Configuration.empty, ws).__timeout.toMillis should (be >= 50L and be <= 60000L)

			ws.close
		}
	}

//	"Microservice#optServiceSecret" should {
//		"read optional service secret from config" in {
//			val ws = MockWS(route1)
//
//			new TestMicroservice("xyzzz", Configuration("microservice.xyzzz.secret" -> "mySecret123"), ws).__optServiceSecret shouldBe Some("mySecret123")
//			new TestMicroservice("other", Configuration("microservice.xyzzz.secret" -> "mySecret123"), ws).__optServiceSecret shouldBe empty
//
//			ws.close
//		}
//	}

	"Microservice#optServiceURL" should {
		"read optional service URL from config" in {
			val ws = MockWS(route1)
			val config = Configuration("microservices.services.servicename.url" -> "http://xyz:1234")

			new TestMicroservice("servicename", config, ws).__optServiceURL shouldBe Some("http://xyz:1234")
			new TestMicroservice("other", config, ws).__optServiceURL shouldBe empty

			ws.close
		}
	}

//	"Microservice#defaultHeaders" should {
//		"contain service secret header if configured" in {
//			val ws = MockWS(route1)
//
//			val headers: Seq[(String, String)] = new TestMicroservice("xyzzz", Configuration("microservice.xyzzz.secret" -> "mySecret123"), ws).__defaultHeaders
//			headers should have length 1
//			headers should contain ("X-Service-Secret", "mySecret123")
//
//			ws.close
//		}
//
//		"not contain service secret header if not configured" in {
//			val ws = MockWS(route1)
//
//			new TestMicroservice("xyzzz", Configuration("microservice.other.secret" -> "mySecret123"), ws).__defaultHeaders should have length 0
//
//			ws.close
//		}
//	}

	"Microservice#endpointURL" should {
		"join service base URL and endpoint if service URL is configured" in {
			val ws = MockWS(route1)
			val config = Configuration("microservices.services.servicename.url" -> "http://xyz:1234")

			new TestMicroservice("servicename", config, ws).__endpointURL("/myendpoint") shouldBe "http://xyz:1234/myendpoint"

			ws.close
		}

		"throw exception if service URL is not configured" in {
			val ws = MockWS(route1)
			val config = Configuration("microservices.services.servicename.url" -> "http://xyz:1234")

			a [RuntimeException] should be thrownBy
				new TestMicroservice("other", config, ws).__endpointURL("/myendpoint")

			ws.close
		}
	}

	"Microservice#handleFutureResponse" should {
		"eventually return Some(WSResponse) if request was made" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futOptWsResponse: Future[Option[WSResponse]] = testMicroservice.getAsOpt("/test")
			whenReady(futOptWsResponse) { (optWsResponse: Option[WSResponse]) =>
				optWsResponse.value.body shouldBe "success"
			}

			ws.close
		}

		// NOTE: We cannot simulate a ConnectionException with MockWS right now:
		//		"eventually return None if request failed due to a ConnectException" in {
		//			val route2 = Route {
		//				case (GET, "http://testservice:8888/test") => Action { Ok("success") }
		//			}
		//			val ws = MockWS(route2)
		//
		//			val testMicroservice = new TestMicroservice("test", config1, ws)
		//			val futOptWsResponse: Future[Option[WSResponse]] = testMicroservice.getAsOpt("/test")
		//			whenReady(futOptWsResponse) { (optWsResponse: Option[WSResponse]) =>
		//				optWsResponse shouldBe empty
		//			}
		//
		//			ws.close
		//		}

		"eventually return None if request failed due to a TimeoutException" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futOptWsResponse: Future[Option[WSResponse]] = testMicroservice.getAsOpt("/slow")
			whenReady(futOptWsResponse) { (optWsResponse: Option[WSResponse]) =>
				optWsResponse shouldBe empty
			}

			ws.close
		}
	}

	"Microservice#asEither" should {
		"be able to eventually return a DefaultServiceResponse on the right when response code is 200" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/test")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.right.value shouldBe a [DefaultServiceResponse]
				eitherErrorOrResponse.right.value.status shouldBe 200
			}

			ws.close
		}

		"be able to eventually return a DefaultServiceResponse on the right when response code is 201" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/create")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.right.value shouldBe a [DefaultServiceResponse]
				eitherErrorOrResponse.right.value.status shouldBe 201
			}

			ws.close
		}

		"be able to eventually return a JsonServiceResponse on the right" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/json")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.right.value shouldBe a [JsonServiceResponse]
			}

			ws.close
		}

		"be able to eventually return a DefaultServiceError on the left" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/notfound")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.left.value shouldBe a [DefaultServiceError]
				eitherErrorOrResponse.left.value.optUnderlyingServiceResponse.value.status shouldBe 404
			}

			ws.close
		}

		"be able to eventually return a DataServiceError on the left" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/jsonbad")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.left.value shouldBe a [DataServiceError]
				eitherErrorOrResponse.left.value.optUnderlyingServiceResponse.value.status shouldBe 400
			}

			ws.close
		}

		"be able to eventually return a DefaultServiceError on the left if JSON response, but not a known dataErrorCode" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/jsontoomany")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.left.value shouldBe a [DefaultServiceError]
				eitherErrorOrResponse.left.value.optUnderlyingServiceResponse.value.status shouldBe 429
			}

			ws.close
		}

		"be able to eventually return an InternalServiceError error if response is JSON, but no object" in {
			val ws = MockWS(route1)

			val testMicroservice = new TestMicroservice("test", config1, ws)
			val futEitherErrorOrResponse: Future[Either[ServiceError, ServiceResponse]] = testMicroservice.getAsEither("/jsonmad")
			whenReady(futEitherErrorOrResponse) { eitherErrorOrResponse =>
				eitherErrorOrResponse.left.value shouldBe a [InternalServiceError]
				eitherErrorOrResponse.left.value.optUnderlyingServiceResponse.value.status shouldBe 400
				eitherErrorOrResponse.left.value.optUnderlyingServiceResponse shouldBe defined
			}

			ws.close
		}
	}

}
