package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/** Se anuló una OT (CU-19). Se publica después del EstadoOrdenCambiado a ANULADA. */
public record OrdenAnulada(
    Long idOrdenTrabajo, String motivo, Long idUsuario, Instant ocurridoEn) {}
