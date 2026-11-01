package com.ciemsa.sigma.administracion.dto;

import java.util.List;

/** Si hay errores no se importó nada (importadas = 0). */
public record ResultadoImportacion(int importadas, List<ErrorFila> errores) {

  public static ResultadoImportacion conErrores(List<ErrorFila> errores) {
    return new ResultadoImportacion(0, List.copyOf(errores));
  }

  public static ResultadoImportacion exitoso(int importadas) {
    return new ResultadoImportacion(importadas, List.of());
  }

  public boolean tieneErrores() {
    return !errores.isEmpty();
  }
}
