package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.Organismo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganismoRepository extends JpaRepository<Organismo, Long> {

  Optional<Organismo> findByNombre(String nombre);
}
