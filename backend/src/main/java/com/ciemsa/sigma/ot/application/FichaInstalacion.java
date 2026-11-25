package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.activos.dto.InstalacionFicha;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.time.Instant;
import java.util.List;

/**
 * Ficha de una instalación (HU-10, CU-7): sus datos (módulo activos), el nombre de su organismo y
 * las OTs en curso, armada por {@link FichaInstalacionService}.
 */
public record FichaInstalacion(
    InstalacionFicha instalacion, Organismo organismo, List<OrdenEnCurso> ordenesEnCurso) {

  public record Organismo(Long id, String nombre) {}

  /**
   * {@code prioridad} y {@code numeroIncidente} son null en las preventivas; {@code cuadrilla}, si
   * no está asignada. {@code puedeVerDetalle}: si el usuario actual puede abrir la OT (un técnico,
   * solo las de su cuadrilla).
   */
  public record OrdenEnCurso(
      Long id,
      String numero,
      TipoOT tipo,
      EstadoOT estado,
      Prioridad prioridad,
      Instant fechaLimite,
      boolean vencida,
      DetalleOt.Cuadrilla cuadrilla,
      String numeroIncidente,
      Instant creadoEn,
      boolean puedeVerDetalle) {}
}
