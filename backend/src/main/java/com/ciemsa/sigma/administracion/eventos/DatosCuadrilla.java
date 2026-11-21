package com.ciemsa.sigma.administracion.eventos;

import com.ciemsa.sigma.administracion.Cuadrilla;

/** Foto de los datos de una cuadrilla para la bitácora. */
public record DatosCuadrilla(String nombre, String zona, boolean activa) {

  public static DatosCuadrilla de(Cuadrilla cuadrilla) {
    return new DatosCuadrilla(cuadrilla.getNombre(), cuadrilla.getZona(), cuadrilla.isActiva());
  }
}
