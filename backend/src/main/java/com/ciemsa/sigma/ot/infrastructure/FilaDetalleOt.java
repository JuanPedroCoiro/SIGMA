package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;

/**
 * Lo que el detalle de una OT lee de otras tablas: instalación, cuadrilla (null si no tiene) e
 * incidente con su categoría (todo null si es preventiva).
 */
public record FilaDetalleOt(
    Long idOrdenTrabajo,
    Long idInstalacion,
    String codigoInstalacion,
    String nombreInstalacion,
    Long idCuadrilla,
    String nombreCuadrilla,
    Long idIncidente,
    String numeroIncidente,
    OrigenIncidente origen,
    String categoria,
    Prioridad prioridad,
    String descripcion,
    String tercero,
    String causa) {}
