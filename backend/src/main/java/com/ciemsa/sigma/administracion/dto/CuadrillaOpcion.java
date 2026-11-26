package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.administracion.Cuadrilla;

/** Una cuadrilla activa a la que el técnico se puede sumar (HU-20). */
public record CuadrillaOpcion(Long id, String nombre, String zona) {

  public static CuadrillaOpcion de(Cuadrilla cuadrilla) {
    return new CuadrillaOpcion(cuadrilla.getId(), cuadrilla.getNombre(), cuadrilla.getZona());
  }
}
