package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** El archivo entero no sirve: vacío, sin filas o sin una columna obligatoria (422). */
public class PlanillaInvalidaException extends ReglaNegocioException {

  public PlanillaInvalidaException(String mensaje) {
    super(mensaje);
  }
}
