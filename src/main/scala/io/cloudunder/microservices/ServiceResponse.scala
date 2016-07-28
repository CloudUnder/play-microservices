package io.cloudunder.microservices

import play.api.libs.ws.WSResponse


trait ServiceResponse {
	val contentType: String
	val status: Int
	val rawResponse: String
	lazy val successStatus: Boolean = status >= 200 && status <= 299
}


object ServiceResponse {
	def apply(wsResponse: WSResponse): ServiceResponse = {
		val contentType = wsResponse.header("Content-Type").getOrElse("")
		val isJson = contentType.startsWith("application/json")

		if (isJson) {
			new JsonServiceResponse(wsResponse)
		} else {
			new DefaultServiceResponse(wsResponse)
		}
	}
}
