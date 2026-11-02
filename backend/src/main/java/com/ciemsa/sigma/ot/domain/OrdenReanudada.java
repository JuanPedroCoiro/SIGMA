package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * Se reanudó una OT. {@code fechaLimite} es la vigente después de reanudar: si el motivo era un
 * tercero, la recalculada; null si el SLA no está definido.
 */
public record OrdenReanudada(
    Long idOrdenTrabajo,
    MotivoPausa motivo,
    Long idUsuario,
    Instant ocurridoEn,
    Instant fechaLimite) {}
