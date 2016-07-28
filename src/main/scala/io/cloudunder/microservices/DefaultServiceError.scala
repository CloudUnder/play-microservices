package io.cloudunder.microservices

case class DefaultServiceError(optUnderlyingServiceResponse: Option[ServiceResponse]) extends ServiceError
