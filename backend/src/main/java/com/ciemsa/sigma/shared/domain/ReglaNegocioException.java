package com.ciemsa.sigma.shared.domain;

/**
 * Los datos son válidos en formato pero violan una regla de negocio, como un técnico sin cuadrilla
 * (400).
 */
public class ReglaNegocioException extends DomainException {

  public ReglaNegocioException(String mensaje) {
    super("regla-negocio", mensaje);
  }
}
