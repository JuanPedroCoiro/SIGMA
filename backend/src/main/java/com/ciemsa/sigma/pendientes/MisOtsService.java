package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja «Mis OTs» del técnico (CU-3, HU-07): solo consultas. Las OTs de su cuadrilla actual
 * (leída de la base) que tiene entre manos, y las pendientes sin asignar que puede tomar (HU-20).
 * Una OT anulada, reasignada o tomada por otra cuadrilla deja de figurar porque cambia su estado o
 * su cuadrilla.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MisOtsService {

  /** Las que la cuadrilla tiene para trabajar; PENDIENTE no tiene cuadrilla. */
  static final List<EstadoOT> ESTADOS =
      List.of(EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  private static final String DE_SU_CUADRILLA =
      "where o.idCuadrilla = :cuadrilla and o.estado in :estados";

  /** Las que se pueden tomar: una pendiente en pausa no se toma (EstadoOT). */
  private static final String SIN_ASIGNAR =
      "where o.idCuadrilla is null and o.estado = com.ciemsa.sigma.ot.domain.EstadoOT.PENDIENTE";

  // La pausa abierta es a lo sumo una (uq_pausa_ot_abierta): el join no duplica filas.
  private static final String SELECCION =
      """
  select new com.ciemsa.sigma.pendientes.FilaOrdenDeMiCuadrilla(
      o.id, o.numero, o.tipo, o.estado, o.estadoAnteriorPausa, inc.prioridad, cat.nombre,
      inc.numero, o.fechaEjecucion, o.fechaLimite, o.iniciadaEn,
      ins.id, ins.codigo, ins.nombre, ins.direccion, ins.rutaZona, ins.latitud, ins.longitud,
      p.motivo, p.detalle, p.inicio, o.motivoObservacion, o.observadaEn)
  from OrdenTrabajo o
      join Instalacion ins on ins.id = o.idInstalacion
      left join o.incidente inc
      left join CategoriaFalla cat on cat.id = inc.idCategoriaFalla
      left join o.pausas p on p.fin is null
  """;

  /** Para trabajar en el día: por fecha de ejecución (las atrasadas primero) y por vencimiento. */
  private static final String POR_FECHA_Y_VENCIMIENTO =
      "\norder by o.fechaEjecucion nulls last, "
          + VencimientoSinEntidad.ORDEN_JPQL
          + ", o.creadoEn, o.id";

  /** Las sin asignar no tienen fecha de ejecución: la más urgente primero. */
  private static final String POR_VENCIMIENTO =
      "\norder by " + VencimientoSinEntidad.ORDEN_JPQL + ", o.creadoEn, o.id";

  private final EntityManager entityManager;
  private final UsuarioService usuarioService;
  private final UsuarioActual usuarioActual;
  private final Clock clock;

  /**
   * Todas las de la cuadrilla, sin filtro por día, paginadas; el orden de {@code pageable} se
   * ignora. Un técnico sin cuadrilla recibe la página vacía.
   */
  public Page<OrdenDeMiCuadrilla> misOts(Pageable pageable) {
    Optional<Long> cuadrilla = usuarioService.idCuadrillaDe(usuarioActual.id());
    if (cuadrilla.isEmpty()) {
      return Page.empty(pageable);
    }
    return pagina(
        DE_SU_CUADRILLA,
        POR_FECHA_Y_VENCIMIENTO,
        Map.of("cuadrilla", cuadrilla.get(), "estados", ESTADOS),
        pageable);
  }

  /**
   * Pendientes sin asignar que el técnico puede tomar (HU-20, CU-2 1a), por vencimiento y
   * paginadas; el orden de {@code pageable} se ignora. Sin cuadrilla no puede tomar: página vacía.
   */
  public Page<OrdenDeMiCuadrilla> pendientesSinAsignar(Pageable pageable) {
    if (usuarioService.idCuadrillaDe(usuarioActual.id()).isEmpty()) {
      return Page.empty(pageable);
    }
    return pagina(SIN_ASIGNAR, POR_VENCIMIENTO, Map.of(), pageable);
  }

  private Page<OrdenDeMiCuadrilla> pagina(
      String filtro, String orden, Map<String, Object> parametros, Pageable pageable) {
    TypedQuery<FilaOrdenDeMiCuadrilla> consulta =
        entityManager.createQuery(SELECCION + filtro + orden, FilaOrdenDeMiCuadrilla.class);
    TypedQuery<Long> conteo =
        entityManager.createQuery("select count(o) from OrdenTrabajo o " + filtro, Long.class);
    parametros.forEach(
        (nombre, valor) -> {
          consulta.setParameter(nombre, valor);
          conteo.setParameter(nombre, valor);
        });
    List<FilaOrdenDeMiCuadrilla> filas =
        consulta
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    long total = conteo.getSingleResult();
    Instant ahora = Instant.now(clock);
    return new PageImpl<>(
        filas.stream().map(fila -> OrdenDeMiCuadrilla.de(fila, ahora)).toList(), pageable, total);
  }
}
