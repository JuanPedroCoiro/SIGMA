package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** La operación no está permitida en el estado actual de la OT (Fig. 18) (400). */
public class TransicionInvalidaException extends ReglaNegocioException {

  public TransicionInvalidaException(EstadoOT estado, String operacion) {
    super(
        "transicion-invalida",
        "No se puede " + operacion + " una OT en estado " + estado.name() + ".");
  }
}
