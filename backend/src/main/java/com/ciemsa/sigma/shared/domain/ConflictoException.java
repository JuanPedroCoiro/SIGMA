package com.ciemsa.sigma.shared.domain;

/** La operación choca con el estado actual de los datos, por ejemplo un correo repetido (409). */
public class ConflictoException extends DomainException {

  public ConflictoException(String mensaje) {
    this("conflicto", mensaje);
  }

  /** Con un tipo propio, cuando el frontend necesita distinguir el caso (correo-duplicado). */
  public ConflictoException(String tipo, String mensaje) {
    super(tipo, mensaje);
  }
}
