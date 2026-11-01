package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenteRepository extends JpaRepository<Incidente, Long> {}
