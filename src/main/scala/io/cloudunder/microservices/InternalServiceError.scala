package io.cloudunder.microservices

case class InternalServiceError(optUnderlyingServiceResponse: Option[ServiceResponse], optMessage: Option[String] = None) extends ServiceError
