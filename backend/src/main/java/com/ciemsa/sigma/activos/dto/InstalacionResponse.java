package com.ciemsa.sigma.activos.dto;

import com.ciemsa.sigma.activos.Instalacion;
import java.math.BigDecimal;

/** Datos de una instalación para el listado (HU-02); el id lleva a la ficha (HU-10). */
public record InstalacionResponse(
    Long id,
    String codigo,
    String nombre,
    String rutaZona,
    BigDecimal latitud,
    BigDecimal longitud) {

  public static InstalacionResponse de(Instalacion instalacion) {
    return new InstalacionResponse(
        instalacion.getId(),
        instalacion.getCodigo(),
        instalacion.getNombre(),
        instalacion.getRutaZona(),
        instalacion.getLatitud(),
        instalacion.getLongitud());
  }
}
