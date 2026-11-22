package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.MotivoPausa;
import java.time.Instant;

/**
 * La regla de OrdenTrabajo.estaVencida para las consultas que no cargan la entidad (tablero y «Mis
 * OTs»): en pausa por un tercero no vence. Todas las OTs que leen están abiertas.
 */
final class VencimientoSinEntidad {

  /**
   * Orden JPQL por vencimiento ({@code o} es la OT y {@code p} su pausa abierta): primero las que
   * corren, por fecha límite; después las pausadas por un tercero (no vencen hasta reanudar) y al
   * final las que no tienen SLA (sin regla o preventivas). TERCERO es el único MotivoPausa con
   * reiniciaSla(), que JPQL no puede llamar: si cambia esa regla, cambiar también este CASE y
   * {@link #vencida}.
   */
  static final String ORDEN_JPQL =
      """
    case
        when p.motivo = com.ciemsa.sigma.ot.domain.MotivoPausa.TERCERO then 1
        when o.fechaLimite is null then 2
        else 0
    end, o.fechaLimite""";

  private VencimientoSinEntidad() {}

  /** {@code motivoPausa} es el de la pausa abierta, o null si no está en pausa. */
  static boolean vencida(MotivoPausa motivoPausa, Instant fechaLimite, Instant ahora) {
    boolean reiniciaSla = motivoPausa != null && motivoPausa.reiniciaSla();
    return !reiniciaSla && fechaLimite != null && ahora.isAfter(fechaLimite);
  }
}
