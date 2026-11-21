package com.ciemsa.sigma.ot.domain;

import java.time.Duration;
import java.util.Map;

/**
 * Tiempos de una OT abierta: total desde que se generó, en pausa por motivo y efectivo (total menos
 * las pausas).
 */
public record TiemposOT(Duration total, Map<MotivoPausa, Duration> enPausa, Duration efectivo) {}
