package io.cloudunder.microservices

import play.api.libs.ws.WSResponse


class DefaultServiceResponse(_response: WSResponse) extends ServiceResponse {

	lazy val contentType = _response.header("Content-Type").getOrElse("")

	lazy val status: Int = _response.status

	lazy val rawResponse: String = _response.body

}
