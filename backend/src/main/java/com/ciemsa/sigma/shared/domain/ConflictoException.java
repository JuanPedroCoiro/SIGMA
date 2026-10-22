package com.ciemsa.sigma.shared.domain;

/** La operación choca con el estado actual de los datos, por ejemplo un correo repetido (409). */
public class ConflictoException extends DomainException {

  public ConflictoException(String mensaje) {
    super(mensaje);
  }
}
