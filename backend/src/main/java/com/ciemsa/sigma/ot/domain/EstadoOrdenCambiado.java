package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Una OT pasó de un estado a otro. En una reasignación los dos estados son ASIGNADA. */
public record EstadoOrdenCambiado(
    Long idOrdenTrabajo, EstadoOT estadoAnterior, EstadoOT estadoNuevo, Instant ocurridoEn) {}
