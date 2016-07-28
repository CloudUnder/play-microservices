package io.cloudunder.microservices

import play.api.libs.json.JsObject


case class DataServiceError(optUnderlyingServiceResponse: Option[ServiceResponse], json: JsObject) extends ServiceError {

	override def toString: String = {
		val statusCode = optUnderlyingServiceResponse.map(u => u.status).getOrElse(0)
		s"Data Service Error (Status Code: $statusCode): ${json.toString}"
	}

}
