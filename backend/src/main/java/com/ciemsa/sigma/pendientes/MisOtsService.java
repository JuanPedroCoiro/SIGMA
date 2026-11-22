package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja «Mis OTs» del técnico (CU-3, HU-07): solo consultas. Las OTs de su cuadrilla actual
 * (leída de la base) que tiene entre manos. Una OT anulada o reasignada deja de figurar porque
 * cambia su estado o su cuadrilla.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MisOtsService {

  /** Las que la cuadrilla tiene para trabajar; PENDIENTE no tiene cuadrilla. */
  static final List<EstadoOT> ESTADOS =
      List.of(EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  private static final String FILTRO = "where o.idCuadrilla = :cuadrilla and o.estado in :estados";

  // La pausa abierta es a lo sumo una (uq_pausa_ot_abierta): el join no duplica filas. Para
  // trabajar en el día: por fecha de ejecución (las atrasadas primero) y después por vencimiento.
  private static final String CONSULTA =
      """
    select new com.ciemsa.sigma.pendientes.FilaOrdenDeMiCuadrilla(
        o.id, o.numero, o.tipo, o.estado, o.estadoAnteriorPausa, inc.prioridad, cat.nombre,
        inc.numero, o.fechaEjecucion, o.fechaLimite, o.iniciadaEn,
        ins.id, ins.codigo, ins.nombre, ins.direccion, ins.rutaZona, ins.latitud, ins.longitud,
        p.motivo, p.detalle, p.inicio)
    from OrdenTrabajo o
        join Instalacion ins on ins.id = o.idInstalacion
        left join o.incidente inc
        left join CategoriaFalla cat on cat.id = inc.idCategoriaFalla
        left join o.pausas p on p.fin is null
    """
          + FILTRO
          + "\norder by o.fechaEjecucion nulls last, "
          + VencimientoSinEntidad.ORDEN_JPQL
          + ", o.creadoEn, o.id";

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
    List<FilaOrdenDeMiCuadrilla> filas =
        entityManager
            .createQuery(CONSULTA, FilaOrdenDeMiCuadrilla.class)
            .setParameter("cuadrilla", cuadrilla.get())
            .setParameter("estados", ESTADOS)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    long total =
        entityManager
            .createQuery("select count(o) from OrdenTrabajo o " + FILTRO, Long.class)
            .setParameter("cuadrilla", cuadrilla.get())
            .setParameter("estados", ESTADOS)
            .getSingleResult();
    Instant ahora = Instant.now(clock);
    return new PageImpl<>(
        filas.stream().map(fila -> OrdenDeMiCuadrilla.de(fila, ahora)).toList(), pageable, total);
  }
}
