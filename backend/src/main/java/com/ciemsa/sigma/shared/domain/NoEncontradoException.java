package com.ciemsa.sigma.shared.domain;

/** No existe (404). */
public class NoEncontradoException extends DomainException {

  public NoEncontradoException(String mensaje) {
    super(mensaje);
  }
}
