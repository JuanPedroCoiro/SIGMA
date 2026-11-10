package com.ciemsa.sigma.shared.domain;

/**
 * Los datos son válidos en formato pero violan una regla de negocio, como un técnico sin cuadrilla
 * (400).
 */
public class ReglaNegocioException extends DomainException {

  public ReglaNegocioException(String mensaje) {
    this("regla-negocio", mensaje);
  }

  /** Con un tipo propio, cuando el frontend necesita distinguir el caso (transicion-invalida). */
  public ReglaNegocioException(String tipo, String mensaje) {
    super(tipo, mensaje);
  }
}
