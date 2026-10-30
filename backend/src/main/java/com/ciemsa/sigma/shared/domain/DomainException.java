package com.ciemsa.sigma.shared.domain;

/**
 * Base de las excepciones de negocio. El tipo es el último tramo del type del ProblemDetail
 * (/errores/<tipo>), con el que el frontend elige qué mostrar.
 */
public abstract class DomainException extends RuntimeException {

  private final String tipo;

  protected DomainException(String tipo, String mensaje) {
    super(mensaje);
    this.tipo = tipo;
  }

  public String getTipo() {
    return tipo;
  }
}
