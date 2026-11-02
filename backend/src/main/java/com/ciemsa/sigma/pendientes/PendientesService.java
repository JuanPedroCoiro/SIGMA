package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablero de pendientes (CU-9, HU-04): solo consultas. Lee OTs e incidentes con joins a las
 * instalaciones y cuadrillas, sin pasar por las entidades de esos módulos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PendientesService {

  /** Las OTs que todavía requieren acción. */
  private static final List<EstadoOT> ESTADOS_PENDIENTES =
      List.of(EstadoOT.PENDIENTE, EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  // La pausa abierta es a lo sumo una (uq_pausa_ot_abierta): el join no duplica filas.
  private static final String CONSULTA_ORDENES =
      """
    select new com.ciemsa.sigma.pendientes.FilaOrdenPendiente(
        o.id, o.numero, o.tipo, o.estado, inc.prioridad, o.fechaLimite,
        ins.codigo, ins.nombre, c.nombre, inc.numero,
        p.motivo, p.detalle, p.inicio, o.creadoEn)
    from OrdenTrabajo o
        join Instalacion ins on ins.id = o.idInstalacion
        left join o.incidente inc
        left join Cuadrilla c on c.id = o.idCuadrilla
        left join o.pausas p on p.fin is null
    where o.estado in :estados
    order by\s""";

  // Primero las que corren, por fecha límite; después las pausadas por un tercero (no vencen
  // hasta reanudar) y al final las que no tienen SLA (sin regla o preventivas). TERCERO es el único
  // MotivoPausa con reiniciaSla(), que JPQL no puede llamar: si cambia esa regla, cambiar también
  // este CASE y OrdenPendiente.vencida.
  private static final String POR_VENCIMIENTO =
      """
    case
        when p.motivo = com.ciemsa.sigma.ot.domain.MotivoPausa.TERCERO then 1
        when o.fechaLimite is null then 2
        else 0
    end, o.fechaLimite, o.creadoEn, o.id""";

  // ALTA, MEDIA, BAJA y al final las preventivas (sin prioridad); el texto ordenaría ALTA, BAJA.
  private static final String POR_PRIORIDAD =
      """
    case inc.prioridad
        when com.ciemsa.sigma.ot.domain.Prioridad.ALTA then 0
        when com.ciemsa.sigma.ot.domain.Prioridad.MEDIA then 1
        when com.ciemsa.sigma.ot.domain.Prioridad.BAJA then 2
        else 3
    end, o.fechaLimite, o.creadoEn, o.id""";

  private static final String POR_CREACION = "o.creadoEn, o.id";

  private final EntityManager entityManager;
  private final Clock clock;

  /** OTs abiertas, paginadas en el orden pedido. El orden de {@code pageable} se ignora. */
  public Page<OrdenPendiente> ordenes(CriterioOrden criterio, Pageable pageable) {
    List<FilaOrdenPendiente> filas =
        entityManager
            .createQuery(CONSULTA_ORDENES + ordenDe(criterio), FilaOrdenPendiente.class)
            .setParameter("estados", ESTADOS_PENDIENTES)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    long total =
        entityManager
            .createQuery(
                "select count(o) from OrdenTrabajo o where o.estado in :estados", Long.class)
            .setParameter("estados", ESTADOS_PENDIENTES)
            .getSingleResult();
    Instant ahora = Instant.now(clock);
    return new PageImpl<>(
        filas.stream().map(fila -> OrdenPendiente.de(fila, ahora)).toList(), pageable, total);
  }

  /** Incidentes en espera de tercero, del más viejo al más nuevo. Son pocos: no se paginan. */
  public List<IncidenteEnEspera> incidentesEnEspera() {
    return entityManager
        .createQuery(
            """
        select new com.ciemsa.sigma.pendientes.IncidenteEnEspera(
            i.id, i.numero, i.prioridad, ins.codigo, ins.nombre,
            i.descripcion, i.tercero, i.causa, i.creadoEn)
        from Incidente i
            join Instalacion ins on ins.id = i.idInstalacion
        where i.estado = :estado
        order by i.creadoEn, i.id""",
            IncidenteEnEspera.class)
        .setParameter("estado", EstadoIncidente.EN_ESPERA_TERCERO)
        .getResultList();
  }

  private static String ordenDe(CriterioOrden criterio) {
    return switch (criterio) {
      case VENCIMIENTO -> POR_VENCIMIENTO;
      case PRIORIDAD -> POR_PRIORIDAD;
      case CREACION -> POR_CREACION;
    };
  }
}
