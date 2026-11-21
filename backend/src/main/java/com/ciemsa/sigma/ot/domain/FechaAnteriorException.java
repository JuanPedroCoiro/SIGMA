package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** La fecha de ejecución es anterior a hoy (HU-05, 2a) (400). */
public class FechaAnteriorException extends ReglaNegocioException {

  public FechaAnteriorException() {
    super("fecha-anterior", "La fecha de ejecución no puede ser anterior a hoy.");
  }
}
