package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Se pausó una OT. Se publica junto con el EstadoOrdenCambiado a EN_PAUSA. */
public record OrdenPausada(
    Long idOrdenTrabajo, MotivoPausa motivo, String detalle, Long idUsuario, Instant ocurridoEn) {}
