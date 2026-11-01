package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrdenTrabajoRepository
    extends JpaRepository<OrdenTrabajo, Long>, JpaSpecificationExecutor<OrdenTrabajo> {}
