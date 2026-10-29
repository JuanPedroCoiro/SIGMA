package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.administracion.Cuadrilla;

/** Cuadrilla para los desplegables y el detalle (catálogo chico, sin paginar). */
public record CuadrillaResponse(Long id, String nombre, String zona, boolean activa) {

  public static CuadrillaResponse de(Cuadrilla cuadrilla) {
    return new CuadrillaResponse(
        cuadrilla.getId(), cuadrilla.getNombre(), cuadrilla.getZona(), cuadrilla.isActiva());
  }
}
