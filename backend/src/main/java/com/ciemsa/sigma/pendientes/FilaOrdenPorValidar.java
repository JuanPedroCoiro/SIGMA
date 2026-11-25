package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;

/** Fila plana de la consulta de OTs por validar: JPQL no arma records anidados. */
public record FilaOrdenPorValidar(
    Long id,
    String numero,
    TipoOT tipo,
    Prioridad prioridad,
    String numeroIncidente,
    String codigoInstalacion,
    String nombreInstalacion,
    String nombreCuadrilla,
    Instant cerradaEn,
    Instant fechaLimite,
    Instant observadaEn) {}
