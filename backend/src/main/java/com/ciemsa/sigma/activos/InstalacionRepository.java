package com.ciemsa.sigma.activos;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstalacionRepository extends JpaRepository<Instalacion, Long> {

  @Query("select i.codigo from Instalacion i where i.codigo in :codigos")
  Set<String> findCodigosExistentes(Collection<String> codigos);
}
