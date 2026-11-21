package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** La operación no está permitida en el estado actual de la OT (Fig. 18) (400). */
public class TransicionInvalidaException extends ReglaNegocioException {

  private final EstadoOT estado;
  private final String operacion;

  public TransicionInvalidaException(EstadoOT estado, String operacion) {
    this(null, estado, operacion);
  }

  private TransicionInvalidaException(String numeroOt, EstadoOT estado, String operacion) {
    super(
        "transicion-invalida",
        (numeroOt == null ? "" : numeroOt + ": ")
            + "No se puede "
            + operacion
            + " una OT en estado "
            + estado.name()
            + ".");
    this.estado = estado;
    this.operacion = operacion;
  }

  /** La misma excepción con el número de la OT adelante, cuando se operan varias a la vez. */
  public TransicionInvalidaException deLaOt(String numeroOt) {
    return new TransicionInvalidaException(numeroOt, estado, operacion);
  }
}
