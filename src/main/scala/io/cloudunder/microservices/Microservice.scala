package io.cloudunder.microservices

import java.net.ConnectException

import play.api.Configuration
import play.api.libs.json.JsObject
import play.api.libs.ws._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}


/**
  * Lowest base class for calling microservices.
  */
abstract class Microservice(serviceName: String, configuration: Configuration, ws: WSClient)(implicit context: ExecutionContext) {

	protected lazy val dataErrorCodes: Set[Int] = configuration.getIntSeq("microservices.dataErrorCodes").map(a => a.map(b => b.toInt).toSet).getOrElse(Set(400, 409, 410))
	protected lazy val timeout: FiniteDuration = configuration.getMilliseconds("microservices.timeout").map(FiniteDuration.apply(_, MILLISECONDS)).getOrElse(1000.milliseconds)
//	protected lazy val optServiceSecret: Option[String] = configuration.getString(s"microservices.services.$serviceName.secret")
	protected lazy val optServiceURL: Option[String] = configuration.getString(s"microservices.services.$serviceName.url")
//	protected lazy val defaultHeaders: Seq[(String, String)] = {
//		optServiceSecret map { serviceSecret =>
//			Seq(("X-Service-Secret", serviceSecret))
//		} getOrElse {
//			Seq()
//		}
//	}

	protected def endpointURL(endpoint: String): String = {
		optServiceURL.map(_ + endpoint).getOrElse {
			//Logger.error(s"""Service URL undefined: $serviceName""")
			throw new RuntimeException("Service URL undefined")
		}
	}

	protected def handleFutureResponse(futureResponse: Future[WSResponse]): Future[Option[WSResponse]] = {
		futureResponse map { response =>
			Some(response)
		} recover {
			case ce: ConnectException =>
				//Logger.warn(s"""Service "$serviceName" connection error: ${ce.getMessage}""")
				None
			case to: TimeoutException =>
				//Logger.warn(s"""Service "$serviceName" connection timeout: ${to.getMessage}""")
				None
		}
	}

	protected def mkRequestHolder(endpoint: String): WSRequest = {
		ws.url(endpointURL(endpoint)).withRequestTimeout(timeout)
	}

	protected def get(endpoint: String): Future[Option[WSResponse]] = {
		val futResponse = mkRequestHolder(endpoint).get
		handleFutureResponse(futResponse)
	}

	/**
	  * Turns a `Future[Option[WSResponse]]` into an `Future[Either[ServiceError, ServiceResponse]]` to make handling
	  * service calls easier and to provide more flexibility at the same time.
	  *
	  * Example: Instead of calling a service endpoint like `postForm(endpointAccountCreate, data)`, simply wrap
	  * this into this function like `asEither(postForm(endpointAccountCreate, data))`.
	  * The function will match the response (or no response) against several rules and return useful matchable
	  * results.
	  *
	  * The return value is `Either` a `Left[ServiceError]` or a `Right[ServiceResponse]` and it will only be a
	  * `Right` if the HTTP status code was a 2xx success.
	  *
	  * The `Left` can be any sub-type of [[ServiceError]], i.e. [[DefaultServiceError]] or [[InternalServiceError]].
	  * Unless the request failed completely (i.e. no server response at all), a [[ServiceError]] contains the
	  * underlying [[ServiceResponse]] object (`optUnderlyingServiceResponse: Option[ServiceResponse]`), which
	  * may be used to handle errors more detailled where required. Of course, the actual [[ServiceResponse]]
	  * can be any sub-type of [[ServiceResponse]], i.e. a [[DefaultServiceResponse]] or a [[JsonServiceResponse]].
	  *
	  * The `Right` can be any sub-type of [[ServiceResponse]], i.e. [[DefaultServiceResponse]] or, if the response
	  * is a JSON object, a [[JsonServiceResponse]].
	  *
	  * @todo This function has not too much to do with the class [[Microservice]] itself. It would probably make
	  *       a bit more sense to write a custom class for our service call responses, basically holding a
	  *       `Option[WSResponse]`, and add the `asEither` function there as an actual method. Alternatively we
	  *       could consider making this `Either` response compulsory for all service call methods (get, post...).
	  */
	protected def asEither(futOptWSResponse: Future[Option[WSResponse]]): Future[Either[ServiceError, ServiceResponse] with Product with Serializable] = {
		for {
			optWSResponse <- futOptWSResponse
		} yield {
			optWSResponse map ServiceResponse.apply map { (serviceResponse: ServiceResponse) =>
				serviceResponse match {
					case jsonServiceResponse: JsonServiceResponse =>
						// We've got a response with a JsValue
						if (jsonServiceResponse.successStatus) {
							// We've got a success HTTP status and a JsValue
							Right(jsonServiceResponse)
						} else {
							// We've got a non-success HTTP status and a JsValue
							jsonServiceResponse.json.asOpt[JsObject] map { jsObj =>
								// The JsValue is actually a JsObject
								if (dataErrorCodes.contains(jsonServiceResponse.status)) {
									// Status code is a certain 4xx Client Error, which indicates something must have
									// been wrong with the data submitted to the service, hence the DataServiceError.
									Left(DataServiceError(Some(jsonServiceResponse), jsObj))
								} else {
									// Status code is not a certain 4xx error, which indicates something else must
									// have gone wrong. In order to let the caller be in control of how to handle this
									// error, return a DefaultServiceError.
									Left(DefaultServiceError(Some(jsonServiceResponse)))
								}
							} getOrElse {
								// The JsValue is not a JsObject, which means it some kind of unexpected error
								Left(InternalServiceError(Some(jsonServiceResponse), None))
							}
						}

					case anyServiceResponse: ServiceResponse =>
						// We've got any other kind of ServiceResponse, which should be a DefaultServiceResponse, because
						// at this point there are no other ServiceResponse types implemented, but we don't have to care
						// about this here as we simply pass it through.
						if (anyServiceResponse.successStatus) {
							// We've got a success HTTP status and a non-JSON body
							Right(anyServiceResponse)
						} else {
							// We've got a non-success HTTP status and a non-JSON body
							Left(DefaultServiceError(Some(anyServiceResponse)))
						}

//					case _ =>
//						// This should never happen and it can never happen, because we match against a ServiceResponse
//						// object and the previous case handles any kind of ServiceResponse.
				}
			} getOrElse {
				// We didn't get a ServiceResponse, which means the request failed completely
				Left(InternalServiceError(None, None))
			}
		}
	}


//	protected def get: Future[Option[WSResponse]]
//	protected def postString: Future[Option[WSResponse]]
//	protected def postForm: Future[Option[WSResponse]]

}
