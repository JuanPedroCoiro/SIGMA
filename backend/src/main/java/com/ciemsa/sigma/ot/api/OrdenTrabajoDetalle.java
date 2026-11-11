package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.DetalleOt;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.PausaOT;
import com.ciemsa.sigma.ot.domain.TiemposOT;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Detalle de una OT: estado, instalación, cuadrilla, incidente, SLA, pausa en curso, tiempos e
 * historial de pausas con quién pausó y reanudó (#57). {@code cuadrilla} es null si no está
 * asignada; {@code incidente}, si es preventiva; {@code tiempos}, si la OT ya no está abierta.
 */
public record OrdenTrabajoDetalle(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    DetalleOt.Instalacion instalacion,
    DetalleOt.Cuadrilla cuadrilla,
    DetalleOt.Incidente incidente,
    LocalDate fechaEjecucion,
    Instant inicioSla,
    Instant fechaLimite,
    boolean slaDefinido,
    boolean vencida,
    Pausa pausaAbierta,
    Tiempos tiempos,
    List<Pausa> pausas) {

  static OrdenTrabajoDetalle de(DetalleOt detalle) {
    OrdenTrabajo ot = detalle.ordenTrabajo();
    List<Pausa> pausas =
        detalle.pausas().stream().map(p -> Pausa.de(p, detalle.nombresUsuarios())).toList();
    return new OrdenTrabajoDetalle(
        ot.getId(),
        ot.getNumero(),
        ot.getTipo(),
        ot.getEstado(),
        detalle.instalacion(),
        detalle.cuadrilla(),
        detalle.incidente(),
        ot.getFechaEjecucion(),
        ot.getInicioSla(),
        ot.getFechaLimite(),
        ot.getFechaLimite() != null,
        detalle.vencida(),
        pausas.stream().filter(p -> p.fin() == null).findFirst().orElse(null),
        Tiempos.de(detalle.tiempos()),
        pausas);
  }

  /** {@code nombreUsuarioReanudacion} es null mientras la pausa sigue abierta. */
  public record Pausa(
      MotivoPausa motivo,
      String detalle,
      Instant inicio,
      Instant fin,
      Long idUsuarioPausa,
      String nombreUsuarioPausa,
      Long idUsuarioReanudacion,
      String nombreUsuarioReanudacion) {

    static Pausa de(PausaOT pausa, Map<Long, String> nombresUsuarios) {
      Long reanudo = pausa.getIdUsuarioReanudacion();
      return new Pausa(
          pausa.getMotivo(),
          pausa.getDetalle(),
          pausa.getInicio(),
          pausa.getFin(),
          pausa.getIdUsuarioPausa(),
          nombresUsuarios.get(pausa.getIdUsuarioPausa()),
          reanudo,
          reanudo == null ? null : nombresUsuarios.get(reanudo));
    }
  }

  /** En minutos enteros. */
  public record Tiempos(
      long totalMinutos, Map<MotivoPausa, Long> enPausaMinutos, long efectivoMinutos) {

    static Tiempos de(TiemposOT tiempos) {
      if (tiempos == null) {
        return null;
      }
      Map<MotivoPausa, Long> enPausa = new EnumMap<>(MotivoPausa.class);
      tiempos.enPausa().forEach((motivo, duracion) -> enPausa.put(motivo, duracion.toMinutes()));
      return new Tiempos(tiempos.total().toMinutes(), enPausa, tiempos.efectivo().toMinutes());
    }
  }
}
