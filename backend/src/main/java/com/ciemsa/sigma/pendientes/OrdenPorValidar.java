package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;

/**
 * Una OT cerrada técnicamente que espera la validación de la Jefatura (HU-09, CU-5 paso 1). {@code
 * prioridad} y {@code numeroIncidente} son null en las preventivas. {@code cerradaEnPlazo} se mide
 * con la fecha del cierre técnico, no con la de validación; es null si la OT no tiene SLA. {@code
 * yaObservada}: volvió a cerrarse después de una observación.
 */
public record OrdenPorValidar(
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
    Boolean cerradaEnPlazo,
    boolean yaObservada) {

  static OrdenPorValidar de(FilaOrdenPorValidar fila) {
    return new OrdenPorValidar(
        fila.id(),
        fila.numero(),
        fila.tipo(),
        fila.prioridad(),
        fila.numeroIncidente(),
        fila.codigoInstalacion(),
        fila.nombreInstalacion(),
        fila.nombreCuadrilla(),
        fila.cerradaEn(),
        fila.fechaLimite(),
        OrdenTrabajo.cumplePlazo(fila.cerradaEn(), fila.fechaLimite()),
        fila.observadaEn() != null);
  }
}
