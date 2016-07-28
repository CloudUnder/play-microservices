package io.cloudunder.microservices

import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse


class JsonServiceResponse(_response: WSResponse) extends DefaultServiceResponse(_response: WSResponse) {

	lazy val json: JsValue = {
		_response.json
	}

}
