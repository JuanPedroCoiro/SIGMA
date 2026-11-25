package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Fila plana de la consulta de «Mis OTs»: JPQL no arma records anidados. */
public record FilaOrdenDeMiCuadrilla(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    EstadoOT estadoAnteriorPausa,
    Prioridad prioridad,
    String categoria,
    String numeroIncidente,
    LocalDate fechaEjecucion,
    Instant fechaLimite,
    Instant iniciadaEn,
    Long idInstalacion,
    String codigoInstalacion,
    String nombreInstalacion,
    String direccion,
    String rutaZona,
    BigDecimal latitud,
    BigDecimal longitud,
    MotivoPausa motivoPausa,
    String detallePausa,
    Instant inicioPausa,
    String motivoObservacion,
    Instant observadaEn) {}
