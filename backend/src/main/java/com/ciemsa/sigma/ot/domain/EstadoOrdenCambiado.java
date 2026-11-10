package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * Una OT pasó de un estado a otro. La reasignación no cambia el estado: se registra con {@link
 * OrdenAsignada}.
 */
public record EstadoOrdenCambiado(
    Long idOrdenTrabajo, EstadoOT estadoAnterior, EstadoOT estadoNuevo, Instant ocurridoEn) {}
