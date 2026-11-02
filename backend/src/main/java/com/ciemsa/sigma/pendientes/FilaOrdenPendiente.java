package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;

/**
 * Fila plana de la consulta del tablero: JPQL no arma records anidados. Instalación, cuadrilla e
 * incidente salen de joins al leer; la pausa, de la abierta (si la hay).
 */
public record FilaOrdenPendiente(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    Prioridad prioridad,
    Instant fechaLimite,
    String codigoInstalacion,
    String nombreInstalacion,
    String cuadrilla,
    String numeroIncidente,
    MotivoPausa motivoPausa,
    String detallePausa,
    Instant inicioPausa,
    Instant creadoEn) {}
