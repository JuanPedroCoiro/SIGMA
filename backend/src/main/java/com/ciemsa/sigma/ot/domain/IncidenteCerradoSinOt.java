package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Se cerró un incidente en espera de tercero sin generar OT (CU-1 4b). */
public record IncidenteCerradoSinOt(Long idIncidente, Long idUsuario, Instant ocurridoEn) {}
