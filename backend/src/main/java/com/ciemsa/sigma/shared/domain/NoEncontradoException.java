package com.ciemsa.sigma.shared.domain;

/** El recurso pedido no existe (404). */
public class NoEncontradoException extends DomainException {

  public NoEncontradoException(String mensaje) {
    super("no-encontrado", mensaje);
  }
}
