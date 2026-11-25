package com.ciemsa.sigma.activos.dto;

import com.ciemsa.sigma.activos.Instalacion;
import java.math.BigDecimal;

/**
 * Datos de una instalación para su ficha (HU-10, CU-7), activa o no. {@code tipo} y {@code
 * direccion} son null mientras no se carguen (2b). El organismo va por id: su nombre lo resuelve el
 * módulo ot, que arma la ficha.
 */
public record InstalacionFicha(
    Long id,
    String codigo,
    String nombre,
    String tipo,
    String direccion,
    String rutaZona,
    BigDecimal latitud,
    BigDecimal longitud,
    Long idOrganismo,
    boolean activa) {

  public static InstalacionFicha de(Instalacion instalacion) {
    return new InstalacionFicha(
        instalacion.getId(),
        instalacion.getCodigo(),
        instalacion.getNombre(),
        instalacion.getTipo(),
        instalacion.getDireccion(),
        instalacion.getRutaZona(),
        instalacion.getLatitud(),
        instalacion.getLongitud(),
        instalacion.getIdOrganismo(),
        instalacion.isActiva());
  }
}
