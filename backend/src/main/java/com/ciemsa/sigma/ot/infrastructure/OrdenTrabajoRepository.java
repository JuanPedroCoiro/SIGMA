package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrdenTrabajoRepository
    extends JpaRepository<OrdenTrabajo, Long>, JpaSpecificationExecutor<OrdenTrabajo> {

  /** La OT correctiva de un incidente: a lo sumo una (CU-1). */
  Optional<OrdenTrabajo> findByIncidenteId(Long idIncidente);

  /** Cuántas OTs de la cuadrilla están en alguno de esos estados. */
  long countByIdCuadrillaAndEstadoIn(Long idCuadrilla, Collection<EstadoOT> estados);
}
