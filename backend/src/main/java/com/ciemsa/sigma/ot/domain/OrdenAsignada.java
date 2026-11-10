package com.ciemsa.sigma.ot.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Se asignó o reasignó una OT (HU-05). {@code idCuadrillaAnterior} es null en la primera
 * asignación; si es igual a la nueva, solo cambió la fecha de ejecución.
 */
public record OrdenAsignada(
    Long idOrdenTrabajo,
    Long idCuadrillaAnterior,
    Long idCuadrillaNueva,
    LocalDate fechaEjecucion,
    Instant ocurridoEn) {}
