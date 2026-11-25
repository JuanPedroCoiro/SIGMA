package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OTs cerradas técnicamente que esperan la validación de la Jefatura (HU-09, CU-5 paso 1): solo
 * consultas. La que espera hace más tiempo, primero.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PorValidarService {

  private static final String FILTRO = "where o.estado = :estado";

  private static final String CONSULTA =
      """
    select new com.ciemsa.sigma.pendientes.FilaOrdenPorValidar(
        o.id, o.numero, o.tipo, inc.prioridad, inc.numero, ins.codigo, ins.nombre, c.nombre,
        o.cerradaEn, o.fechaLimite, o.observadaEn)
    from OrdenTrabajo o
        join Instalacion ins on ins.id = o.idInstalacion
        left join o.incidente inc
        left join Cuadrilla c on c.id = o.idCuadrilla
    """
          + FILTRO
          + "\norder by o.cerradaEn, o.id";

  private final EntityManager entityManager;

  /** Paginadas; el orden de {@code pageable} se ignora. */
  public Page<OrdenPorValidar> ordenes(Pageable pageable) {
    List<FilaOrdenPorValidar> filas =
        entityManager
            .createQuery(CONSULTA, FilaOrdenPorValidar.class)
            .setParameter("estado", EstadoOT.CERRADA_TECNICA)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();
    long total =
        entityManager
            .createQuery("select count(o) from OrdenTrabajo o " + FILTRO, Long.class)
            .setParameter("estado", EstadoOT.CERRADA_TECNICA)
            .getSingleResult();
    return new PageImpl<>(filas.stream().map(OrdenPorValidar::de).toList(), pageable, total);
  }
}
