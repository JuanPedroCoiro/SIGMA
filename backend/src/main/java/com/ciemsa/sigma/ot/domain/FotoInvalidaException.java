package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** La foto está vacía, supera los 10 MB o no es JPG, PNG ni WEBP (#71) (400). */
public class FotoInvalidaException extends ReglaNegocioException {

  public FotoInvalidaException(String mensaje) {
    super("foto-invalida", mensaje);
  }
}
