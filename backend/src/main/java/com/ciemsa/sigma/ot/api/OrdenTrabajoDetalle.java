package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.DetalleOt;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.PausaOT;
import com.ciemsa.sigma.ot.domain.TiemposOT;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Detalle de una OT: estado, SLA, pausa en curso, tiempos e historial de pausas. {@code tiempos} es
 * null si la OT ya no está abierta.
 */
public record OrdenTrabajoDetalle(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    Long idCuadrilla,
    Instant inicioSla,
    Instant fechaLimite,
    boolean slaDefinido,
    boolean vencida,
    Pausa pausaAbierta,
    Tiempos tiempos,
    List<Pausa> pausas) {

  static OrdenTrabajoDetalle de(DetalleOt detalle) {
    OrdenTrabajo ot = detalle.ordenTrabajo();
    List<Pausa> pausas = detalle.pausas().stream().map(Pausa::de).toList();
    return new OrdenTrabajoDetalle(
        ot.getId(),
        ot.getNumero(),
        ot.getTipo(),
        ot.getEstado(),
        ot.getIdCuadrilla(),
        ot.getInicioSla(),
        ot.getFechaLimite(),
        ot.getFechaLimite() != null,
        detalle.vencida(),
        pausas.stream().filter(p -> p.fin() == null).findFirst().orElse(null),
        Tiempos.de(detalle.tiempos()),
        pausas);
  }

  public record Pausa(
      MotivoPausa motivo,
      String detalle,
      Instant inicio,
      Instant fin,
      Long idUsuarioPausa,
      Long idUsuarioReanudacion) {

    static Pausa de(PausaOT pausa) {
      return new Pausa(
          pausa.getMotivo(),
          pausa.getDetalle(),
          pausa.getInicio(),
          pausa.getFin(),
          pausa.getIdUsuarioPausa(),
          pausa.getIdUsuarioReanudacion());
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
