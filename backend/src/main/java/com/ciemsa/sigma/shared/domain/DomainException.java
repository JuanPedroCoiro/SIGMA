package com.ciemsa.sigma.shared.domain;

/** Base de las excepciones de negocio. El GlobalExceptionHandler las traduce a ProblemDetail. */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String mensaje) {
    super(mensaje);
  }
}
