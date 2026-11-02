package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.Prioridad;
import java.time.Instant;

/** Un incidente en espera de tercero (CU-1 4b): sin OT ni vencimiento hasta que se resuelva. */
public record IncidenteEnEspera(
    Long id,
    String numero,
    Prioridad prioridad,
    String codigoInstalacion,
    String nombreInstalacion,
    String descripcion,
    String tercero,
    String causa,
    Instant creadoEn) {}
