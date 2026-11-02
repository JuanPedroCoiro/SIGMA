package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.PausaOT;
import com.ciemsa.sigma.ot.domain.TiemposOT;
import java.time.Instant;
import java.util.List;

/**
 * Una OT con lo que se calcula al consultarla, armado dentro de la transacción (las pausas son
 * lazy). {@code tiempos} es null si la OT ya no está abierta.
 */
public record DetalleOt(
    OrdenTrabajo ordenTrabajo, boolean vencida, TiemposOT tiempos, List<PausaOT> pausas) {

  static DetalleOt de(OrdenTrabajo ot, Instant ahora) {
    return new DetalleOt(ot, ot.estaVencida(ahora), ot.tiempos(ahora), List.copyOf(ot.getPausas()));
  }
}
