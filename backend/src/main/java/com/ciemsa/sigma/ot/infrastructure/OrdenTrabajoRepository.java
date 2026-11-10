package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenTrabajoRepository
    extends JpaRepository<OrdenTrabajo, Long>, JpaSpecificationExecutor<OrdenTrabajo> {

  /** La OT correctiva de un incidente: a lo sumo una (CU-1). */
  Optional<OrdenTrabajo> findByIncidenteId(Long idIncidente);

  /** Cuántas OTs de la cuadrilla están en alguno de esos estados. */
  long countByIdCuadrillaAndEstadoIn(Long idCuadrilla, Collection<EstadoOT> estados);

  /**
   * Por cada cuadrilla, cuántas OTs tienen esa fecha de ejecución y están en alguno de esos
   * estados. Las cuadrillas sin ninguna no aparecen.
   */
  @Query(
      """
    select o.idCuadrilla as idCuadrilla, count(o) as cantidad
    from OrdenTrabajo o
    where o.fechaEjecucion = :fecha
      and o.estado in :estados
      and o.idCuadrilla is not null
    group by o.idCuadrilla
    """)
  List<CantidadPorCuadrilla> contarPorCuadrillaParaFecha(
      @Param("fecha") LocalDate fecha, @Param("estados") Collection<EstadoOT> estados);

  /** Una fila de {@link #contarPorCuadrillaParaFecha}. */
  interface CantidadPorCuadrilla {

    Long getIdCuadrilla();

    Long getCantidad();
  }
}
