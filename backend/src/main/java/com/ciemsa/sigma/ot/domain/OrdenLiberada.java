package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * La cuadrilla que tenía la OT la liberó (HU-20): vuelve a Pendiente sin asignar. Se publica
 * después del EstadoOrdenCambiado a PENDIENTE.
 */
public record OrdenLiberada(
    Long idOrdenTrabajo, Long idCuadrilla, Long idUsuario, Instant ocurridoEn) {}
