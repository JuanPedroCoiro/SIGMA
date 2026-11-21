package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.administracion.Cuadrilla;

/** Lo que otros módulos necesitan de una cuadrilla, sin exponer la entidad. */
public record CuadrillaResumen(Long id, String nombre) {

  public static CuadrillaResumen de(Cuadrilla cuadrilla) {
    return new CuadrillaResumen(cuadrilla.getId(), cuadrilla.getNombre());
  }
}
