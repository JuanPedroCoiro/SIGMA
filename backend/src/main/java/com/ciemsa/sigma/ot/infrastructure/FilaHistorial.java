package com.ciemsa.sigma.ot.infrastructure;

import java.time.Instant;

/**
 * Una fila del historial de la instalación (HU-10): una OT terminada ({@code clase} «OT») o un
 * incidente sin OT («INCIDENTE»). Los enums llegan como texto, tal como están en la base; los
 * campos que no corresponden a la clase son null.
 */
public record FilaHistorial(
    String clase,
    Long id,
    String numero,
    String estado,
    String tipo,
    Instant fecha,
    Instant reportadoEn,
    Instant iniciadaEn,
    Instant cerradaEn,
    Long idCuadrilla,
    String cuadrilla,
    Long idIncidente,
    String numeroIncidente,
    String categoria,
    String prioridad,
    String descripcion,
    String diagnostico,
    String solucion,
    String observaciones,
    String motivoAnulacion,
    String tercero,
    String causa) {}
