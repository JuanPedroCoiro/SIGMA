package com.ciemsa.sigma.shared.domain;

import java.util.Map;

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

  /**
   * Datos extra que el ProblemDetail agrega como propiedades, para que el frontend no tenga que
   * leerlos del mensaje (por ejemplo, el estado actual de la OT en una transición inválida).
   */
  public Map<String, Object> getPropiedades() {
    return Map.of();
  }
}
