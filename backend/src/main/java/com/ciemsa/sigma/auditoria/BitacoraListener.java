package com.ciemsa.sigma.auditoria;

import static com.ciemsa.sigma.auditoria.AccionAuditada.ACTIVACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.ALTA;
import static com.ciemsa.sigma.auditoria.AccionAuditada.ANULACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.ASIGNACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.CAMBIO_ESTADO;
import static com.ciemsa.sigma.auditoria.AccionAuditada.CIERRE_SIN_OT;
import static com.ciemsa.sigma.auditoria.AccionAuditada.DESACTIVACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.GENERACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.LIBERACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.MODIFICACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.PAUSA;
import static com.ciemsa.sigma.auditoria.AccionAuditada.REANUDACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.REASIGNACION;
import static com.ciemsa.sigma.auditoria.AccionAuditada.REGISTRO;
import static com.ciemsa.sigma.auditoria.AccionAuditada.RESTABLECIMIENTO_CONTRASENA;
import static com.ciemsa.sigma.auditoria.EntidadAuditada.CUADRILLA;
import static com.ciemsa.sigma.auditoria.EntidadAuditada.INCIDENTE;
import static com.ciemsa.sigma.auditoria.EntidadAuditada.ORDEN_TRABAJO;
import static com.ciemsa.sigma.auditoria.EntidadAuditada.USUARIO;

import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaActivada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaCreada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaDesactivada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaModificada;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.ContrasenaRestablecida;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.UsuarioActivado;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.UsuarioCreado;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.UsuarioDesactivado;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.UsuarioModificado;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOrdenCambiado;
import com.ciemsa.sigma.ot.domain.IncidenteCerradoSinOt;
import com.ciemsa.sigma.ot.domain.IncidenteRegistrado;
import com.ciemsa.sigma.ot.domain.OrdenAnulada;
import com.ciemsa.sigma.ot.domain.OrdenAsignada;
import com.ciemsa.sigma.ot.domain.OrdenGenerada;
import com.ciemsa.sigma.ot.domain.OrdenLiberada;
import com.ciemsa.sigma.ot.domain.OrdenPausada;
import com.ciemsa.sigma.ot.domain.OrdenReanudada;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Pasa los eventos de dominio a la bitácora: un evento, un registro. Escucha de forma sincrónica,
 * dentro de la transacción de la acción ({@link AuditoriaService#registrar} la exige). Para auditar
 * una acción nueva: publicar su evento con quién y cuándo, y mapearlo acá.
 */
@Component
@RequiredArgsConstructor
class BitacoraListener {

  private final AuditoriaService auditoria;

  // --- Incidentes ---

  /**
   * Con los datos que lo clasifican (#80) y el estado inicial (#86), como CIERRE_SIN_OT guarda el
   * suyo; la descripción no, porque es texto libre y largo.
   */
  @EventListener
  public void on(IncidenteRegistrado e) {
    auditoria.registrar(
        INCIDENTE,
        e.idIncidente(),
        REGISTRO,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores(
            "idInstalacion",
            e.idInstalacion(),
            "idCategoriaFalla",
            e.idCategoriaFalla(),
            "origen",
            e.origen(),
            "prioridad",
            e.prioridad(),
            "estado",
            e.estado(),
            "tercero",
            e.tercero(),
            "causa",
            e.causa()));
  }

  @EventListener
  public void on(IncidenteCerradoSinOt e) {
    auditoria.registrar(
        INCIDENTE,
        e.idIncidente(),
        CIERRE_SIN_OT,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores("estado", EstadoIncidente.CERRADO_SIN_OT));
  }

  // --- Órdenes de trabajo ---

  @EventListener
  public void on(OrdenGenerada e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        GENERACION,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores("idIncidente", e.idIncidente()));
  }

  @EventListener
  public void on(EstadoOrdenCambiado e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        CAMBIO_ESTADO,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("estado", e.estadoAnterior()),
        valores("estado", e.estadoNuevo()));
  }

  /** La primera asignación no tiene valor anterior; las siguientes son reasignaciones. */
  @EventListener
  public void on(OrdenAsignada e) {
    boolean primera = e.idCuadrillaAnterior() == null;
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        primera ? ASIGNACION : REASIGNACION,
        e.idUsuario(),
        e.ocurridoEn(),
        primera
            ? null
            : valores(
                "idCuadrilla",
                e.idCuadrillaAnterior(),
                "fechaEjecucion",
                e.fechaEjecucionAnterior()),
        valores("idCuadrilla", e.idCuadrillaNueva(), "fechaEjecucion", e.fechaEjecucion()));
  }

  @EventListener
  public void on(OrdenPausada e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        PAUSA,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores("motivo", e.motivo(), "detalle", e.detalle()));
  }

  @EventListener
  public void on(OrdenReanudada e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        REANUDACION,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores("motivo", e.motivo(), "fechaLimite", e.fechaLimite()));
  }

  /** La cuadrilla que tenía la OT queda como valor anterior: ahora no la tiene nadie (HU-20). */
  @EventListener
  public void on(OrdenLiberada e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        LIBERACION,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("idCuadrilla", e.idCuadrilla()),
        null);
  }

  @EventListener
  public void on(OrdenAnulada e) {
    auditoria.registrar(
        ORDEN_TRABAJO,
        e.idOrdenTrabajo(),
        ANULACION,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        valores("motivo", e.motivo()));
  }

  // --- Usuarios (HU-01): nunca la contraseña ni su hash ---

  @EventListener
  public void on(UsuarioCreado e) {
    auditoria.registrar(
        USUARIO, e.idCuenta(), ALTA, e.idUsuario(), e.ocurridoEn(), null, e.datos());
  }

  @EventListener
  public void on(UsuarioModificado e) {
    auditoria.registrar(
        USUARIO,
        e.idCuenta(),
        MODIFICACION,
        e.idUsuario(),
        e.ocurridoEn(),
        e.anterior(),
        e.nuevo());
  }

  @EventListener
  public void on(ContrasenaRestablecida e) {
    auditoria.registrar(
        USUARIO,
        e.idCuenta(),
        RESTABLECIMIENTO_CONTRASENA,
        e.idUsuario(),
        e.ocurridoEn(),
        null,
        null);
  }

  @EventListener
  public void on(UsuarioDesactivado e) {
    auditoria.registrar(
        USUARIO,
        e.idCuenta(),
        DESACTIVACION,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("activo", true),
        valores("activo", false));
  }

  @EventListener
  public void on(UsuarioActivado e) {
    auditoria.registrar(
        USUARIO,
        e.idCuenta(),
        ACTIVACION,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("activo", false),
        valores("activo", true));
  }

  // --- Cuadrillas ---

  @EventListener
  public void on(CuadrillaCreada e) {
    auditoria.registrar(
        CUADRILLA, e.idCuadrilla(), ALTA, e.idUsuario(), e.ocurridoEn(), null, e.datos());
  }

  @EventListener
  public void on(CuadrillaModificada e) {
    auditoria.registrar(
        CUADRILLA,
        e.idCuadrilla(),
        MODIFICACION,
        e.idUsuario(),
        e.ocurridoEn(),
        e.anterior(),
        e.nuevo());
  }

  @EventListener
  public void on(CuadrillaDesactivada e) {
    auditoria.registrar(
        CUADRILLA,
        e.idCuadrilla(),
        DESACTIVACION,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("activa", true),
        valores("activa", false));
  }

  @EventListener
  public void on(CuadrillaActivada e) {
    auditoria.registrar(
        CUADRILLA,
        e.idCuadrilla(),
        ACTIVACION,
        e.idUsuario(),
        e.ocurridoEn(),
        valores("activa", false),
        valores("activa", true));
  }

  /** Pares clave-valor en orden; admite valores null (Map.of no). */
  private static Map<String, Object> valores(Object... claveValor) {
    Map<String, Object> mapa = new LinkedHashMap<>();
    for (int i = 0; i < claveValor.length; i += 2) {
      mapa.put((String) claveValor[i], claveValor[i + 1]);
    }
    return mapa;
  }
}
