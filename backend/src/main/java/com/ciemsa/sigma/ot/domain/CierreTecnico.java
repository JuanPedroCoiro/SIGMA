package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/**
 * Lo que informa el técnico al cerrar (CU-3, pasos 6 a 10): diagnóstico y solución obligatorios,
 * observaciones opcionales. Se guardan sin espacios de más; observaciones en blanco queda null.
 */
public record CierreTecnico(String diagnostico, String solucion, String observaciones) {

  /** El largo de las columnas de orden_trabajo. */
  public static final int LARGO_MAXIMO = 2000;

  public CierreTecnico {
    diagnostico = obligatorio(diagnostico, "El diagnóstico es obligatorio.");
    solucion = obligatorio(solucion, "La solución es obligatoria.");
    observaciones = observaciones == null || observaciones.isBlank() ? null : observaciones.trim();
    validarLargo(diagnostico, "el diagnóstico");
    validarLargo(solucion, "la solución");
    validarLargo(observaciones, "las observaciones");
  }

  private static String obligatorio(String texto, String mensaje) {
    if (texto == null || texto.isBlank()) {
      throw new ReglaNegocioException(mensaje);
    }
    return texto.trim();
  }

  private static void validarLargo(String texto, String campo) {
    if (texto != null && texto.length() > LARGO_MAXIMO) {
      throw new ReglaNegocioException(
          "Se admiten hasta " + LARGO_MAXIMO + " caracteres en " + campo + ".");
    }
  }
}
