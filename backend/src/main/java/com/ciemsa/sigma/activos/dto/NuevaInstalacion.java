package com.ciemsa.sigma.activos.dto;

import java.math.BigDecimal;

/** Datos mínimos de una instalación nueva, ya validados (RF-29). */
public record NuevaInstalacion(
    String codigo, String nombre, String rutaZona, BigDecimal latitud, BigDecimal longitud) {}
