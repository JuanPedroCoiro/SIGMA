package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Se registró un incidente (CU-1). */
public record IncidenteRegistrado(Long idIncidente, Long idUsuario, Instant ocurridoEn) {}
