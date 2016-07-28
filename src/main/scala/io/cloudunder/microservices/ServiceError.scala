package io.cloudunder.microservices


trait ServiceError {

	val optUnderlyingServiceResponse: Option[ServiceResponse]

	override def toString: String = {
		optUnderlyingServiceResponse match {
			case Some(serviceResponse) =>
				s"Service Error (Status Code: ${serviceResponse.status}): ${serviceResponse.rawResponse}"
			case None =>
				"Service Error without a response"
		}
	}

}
