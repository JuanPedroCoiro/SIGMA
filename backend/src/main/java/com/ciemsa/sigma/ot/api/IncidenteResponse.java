package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.IncidenteConOt;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.Prioridad;
import java.time.Instant;

/** Un incidente y, si la tiene, su OT correctiva (null en espera de tercero o cerrado sin OT). */
public record IncidenteResponse(
    Long id,
    String numero,
    EstadoIncidente estado,
    Prioridad prioridad,
    String tercero,
    String causa,
    OrdenTrabajoResumen ordenTrabajo) {

  static IncidenteResponse de(IncidenteConOt resultado) {
    Incidente incidente = resultado.incidente();
    OrdenTrabajo ot = resultado.ordenTrabajo();
    return new IncidenteResponse(
        incidente.getId(),
        incidente.getNumero(),
        incidente.getEstado(),
        incidente.getPrioridad(),
        incidente.getTercero(),
        incidente.getCausa(),
        ot == null ? null : OrdenTrabajoResumen.de(ot));
  }

  /** {@code slaDefinido} false = no hay regla para el organismo y la prioridad (8a). */
  public record OrdenTrabajoResumen(
      Long id, String numero, Instant inicioSla, Instant fechaLimite, boolean slaDefinido) {

    static OrdenTrabajoResumen de(OrdenTrabajo ot) {
      return new OrdenTrabajoResumen(
          ot.getId(),
          ot.getNumero(),
          ot.getInicioSla(),
          ot.getFechaLimite(),
          ot.getFechaLimite() != null);
    }
  }
}
