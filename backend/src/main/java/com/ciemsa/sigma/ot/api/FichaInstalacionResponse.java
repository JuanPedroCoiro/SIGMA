package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.activos.dto.InstalacionFicha;
import com.ciemsa.sigma.ot.application.DetalleOt;
import com.ciemsa.sigma.ot.application.FichaInstalacion;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ficha de una instalación (HU-10, CU-7): datos, organismo y OTs en curso (Pendiente, Asignada, En
 * proceso y En pausa), primero la que vence antes. {@code tipo} y {@code direccion} son null
 * mientras no se carguen (2b). Las inactivas también se consultan, con {@code activa} en false.
 */
public record FichaInstalacionResponse(
    Long id,
    String codigo,
    String nombre,
    String tipo,
    String direccion,
    String rutaZona,
    BigDecimal latitud,
    BigDecimal longitud,
    boolean activa,
    FichaInstalacion.Organismo organismo,
    List<OrdenEnCurso> ordenesEnCurso) {

  static FichaInstalacionResponse de(FichaInstalacion ficha) {
    InstalacionFicha i = ficha.instalacion();
    return new FichaInstalacionResponse(
        i.id(),
        i.codigo(),
        i.nombre(),
        i.tipo(),
        i.direccion(),
        i.rutaZona(),
        i.latitud(),
        i.longitud(),
        i.activa(),
        ficha.organismo(),
        ficha.ordenesEnCurso().stream().map(OrdenEnCurso::de).toList());
  }

  /**
   * {@code prioridad} y {@code numeroIncidente} son null en las preventivas; {@code cuadrilla}, si
   * no está asignada. {@code puedeVerDetalle} es false para un técnico si la OT no es de su
   * cuadrilla: el detalle le respondería 403.
   */
  public record OrdenEnCurso(
      Long id,
      String numero,
      TipoOT tipo,
      EstadoOT estado,
      Prioridad prioridad,
      Instant fechaLimite,
      boolean slaDefinido,
      boolean vencida,
      DetalleOt.Cuadrilla cuadrilla,
      String numeroIncidente,
      Instant creadoEn,
      boolean puedeVerDetalle) {

    static OrdenEnCurso de(FichaInstalacion.OrdenEnCurso ot) {
      return new OrdenEnCurso(
          ot.id(),
          ot.numero(),
          ot.tipo(),
          ot.estado(),
          ot.prioridad(),
          ot.fechaLimite(),
          ot.fechaLimite() != null,
          ot.vencida(),
          ot.cuadrilla(),
          ot.numeroIncidente(),
          ot.creadoEn(),
          ot.puedeVerDetalle());
    }
  }
}
