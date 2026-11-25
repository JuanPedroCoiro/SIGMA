package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;

/**
 * Una entrada del historial de la instalación (HU-10, CU-8): una OT terminada o un incidente sin
 * OT. {@code fecha} es la de la línea de tiempo: el cierre técnico, la anulación o el reporte del
 * incidente. Los campos que no corresponden a la clase son null.
 */
public record EntradaHistorial(
    Clase clase,
    Long id,
    String numero,
    EstadoOT estadoOt,
    EstadoIncidente estadoIncidente,
    TipoOT tipoOt,
    Instant fecha,
    Instant reportadoEn,
    Instant iniciadaEn,
    Instant cerradaEn,
    DetalleOt.Cuadrilla cuadrilla,
    Incidente incidente,
    String diagnostico,
    String solucion,
    String observaciones,
    String motivoAnulacion,
    String tercero,
    String causa,
    boolean puedeVerDetalle) {

  public enum Clase {
    OT,
    INCIDENTE
  }

  /** El incidente de la OT, o el incidente sin OT; {@code categoria} es el nombre de la falla. */
  public record Incidente(
      Long id, String numero, String categoria, Prioridad prioridad, String descripcion) {}
}
