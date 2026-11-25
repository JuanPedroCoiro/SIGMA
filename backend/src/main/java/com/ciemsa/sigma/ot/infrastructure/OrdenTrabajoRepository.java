package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrdenTrabajoRepository
    extends JpaRepository<OrdenTrabajo, Long>, JpaSpecificationExecutor<OrdenTrabajo> {

  /** La OT con la fila bloqueada hasta el fin de la transacción (cierre idempotente, R8). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrdenTrabajo o where o.id = :id")
  Optional<OrdenTrabajo> findConBloqueoById(@Param("id") Long id);

  /** Si algún cierre ya usó ese identificador de operación. */
  boolean existsByIdOperacionCierre(UUID idOperacionCierre);

  /** La OT correctiva de un incidente: a lo sumo una (CU-1). */
  Optional<OrdenTrabajo> findByIncidenteId(Long idIncidente);

  /** Las OTs de la instalación en alguno de esos estados (ficha, HU-10), con su incidente. */
  @EntityGraph(attributePaths = "incidente")
  List<OrdenTrabajo> findByIdInstalacionAndEstadoIn(
      Long idInstalacion, Collection<EstadoOT> estados);

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
