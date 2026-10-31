package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.domain.DomainException;

/**
 * El archivo entero no sirve: vacío, sin filas o sin una columna obligatoria (422). La traduce
 * ImportacionInstalacionesController, con el mismo type que los errores por fila.
 */
public class PlanillaInvalidaException extends DomainException {

  public PlanillaInvalidaException(String mensaje) {
    super("planilla-invalida", mensaje);
  }
}
