package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReglaSlaRepository extends JpaRepository<ReglaSLA, Long> {

  Optional<ReglaSLA> findByIdOrganismoAndPrioridad(Long idOrganismo, Prioridad prioridad);
}
