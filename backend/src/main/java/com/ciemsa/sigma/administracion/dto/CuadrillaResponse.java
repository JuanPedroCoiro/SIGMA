package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.administracion.Cuadrilla;

/** Cuadrilla con la cantidad de técnicos activos (catálogo chico, sin paginar). */
public record CuadrillaResponse(
    Long id, String nombre, String zona, boolean activa, long tecnicosActivos) {

  public static CuadrillaResponse de(Cuadrilla cuadrilla, long tecnicosActivos) {
    return new CuadrillaResponse(
        cuadrilla.getId(),
        cuadrilla.getNombre(),
        cuadrilla.getZona(),
        cuadrilla.isActiva(),
        tecnicosActivos);
  }
}
