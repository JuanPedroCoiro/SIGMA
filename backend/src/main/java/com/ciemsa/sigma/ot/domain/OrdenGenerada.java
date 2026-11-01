package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Nació una OT. {@code idIncidente} es null en las preventivas. */
public record OrdenGenerada(Long idOrdenTrabajo, Long idIncidente, Instant ocurridoEn) {}
