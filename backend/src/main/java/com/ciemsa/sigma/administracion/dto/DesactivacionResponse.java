package com.ciemsa.sigma.administracion.dto;

/**
 * Respuesta de desactivar un usuario (CU-13d). Si es un técnico, {@code cuadrilla} y {@code
 * otsAbiertas} avisan cuántas OTs asignadas, en proceso o en pausa tiene su cuadrilla, para que la
 * Jefatura reasigne si hace falta (3a). Para los demás roles, null y 0.
 */
public record DesactivacionResponse(String cuadrilla, long otsAbiertas) {

  public static DesactivacionResponse sinAviso() {
    return new DesactivacionResponse(null, 0);
  }
}
