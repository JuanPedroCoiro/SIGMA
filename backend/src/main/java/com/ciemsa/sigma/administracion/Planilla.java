package com.ciemsa.sigma.administracion;

import java.util.List;
import java.util.Map;

/**
 * Contenido de una planilla. Los encabezados vienen normalizados (sin espacios y en minúsculas) y
 * son las claves de los valores de cada fila.
 */
public record Planilla(List<String> encabezados, List<Fila> filas) {

  /** Una fila de datos; numero es la línea del archivo, para informar errores (la primera es 2). */
  public record Fila(int numero, Map<String, String> valores) {

    public String valor(String columna) {
      return valores.getOrDefault(columna, "");
    }
  }
}
