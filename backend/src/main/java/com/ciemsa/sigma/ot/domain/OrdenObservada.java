package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * La Jefatura observó una OT cerrada (HU-09, CU-5 4a): vuelve a Asignada a la misma cuadrilla. Se
 * publica después del EstadoOrdenCambiado a ASIGNADA.
 */
public record OrdenObservada(
    Long idOrdenTrabajo, String motivo, Long idUsuario, Instant ocurridoEn) {}
