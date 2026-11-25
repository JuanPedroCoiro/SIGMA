package com.ciemsa.sigma.ot.api;

/**
 * Validación de una OT cerrada (HU-09, CU-5). {@code aceptarSinFotos}: la Jefatura ya vio la
 * advertencia de que falta la foto Antes o la Después (3a) y valida igual. Sin cuerpo, es false.
 */
public record ValidarOtRequest(boolean aceptarSinFotos) {}
