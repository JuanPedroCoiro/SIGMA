package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;

/**
 * Una OT del tablero de pendientes (CU-9). {@code prioridad} y {@code numeroIncidente} son null en
 * las preventivas; {@code cuadrilla}, si no está asignada; {@code pausa}, si no está en pausa.
 * {@code estadoAnteriorPausa} solo en EN_PAUSA: decide si se puede anular (CU-19).
 */
public record OrdenPendiente(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    EstadoOT estadoAnteriorPausa,
    Prioridad prioridad,
    Instant fechaLimite,
    boolean slaDefinido,
    boolean vencida,
    String codigoInstalacion,
    String nombreInstalacion,
    String cuadrilla,
    String numeroIncidente,
    Pausa pausa,
    Instant creadoEn) {

  static OrdenPendiente de(FilaOrdenPendiente fila, Instant ahora) {
    Pausa pausa =
        fila.motivoPausa() == null
            ? null
            : new Pausa(fila.motivoPausa(), fila.detallePausa(), fila.inicioPausa());
    return new OrdenPendiente(
        fila.id(),
        fila.numero(),
        fila.tipo(),
        fila.estado(),
        fila.estadoAnteriorPausa(),
        fila.prioridad(),
        fila.fechaLimite(),
        fila.fechaLimite() != null,
        vencida(fila, ahora),
        fila.codigoInstalacion(),
        fila.nombreInstalacion(),
        fila.cuadrilla(),
        fila.numeroIncidente(),
        pausa,
        fila.creadoEn());
  }

  /**
   * La misma regla que OrdenTrabajo.estaVencida, sin cargar la entidad: en pausa por un tercero no
   * vence. Todas las OTs del tablero están abiertas.
   */
  private static boolean vencida(FilaOrdenPendiente fila, Instant ahora) {
    boolean reiniciaSla = fila.motivoPausa() != null && fila.motivoPausa().reiniciaSla();
    return !reiniciaSla && fila.fechaLimite() != null && ahora.isAfter(fila.fechaLimite());
  }

  public record Pausa(MotivoPausa motivo, String detalle, Instant inicio) {}
}
