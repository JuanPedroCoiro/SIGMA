package com.ciemsa.sigma.activos;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstalacionRepository extends JpaRepository<Instalacion, Long> {

  @Query("select i.codigo from Instalacion i where i.codigo in :codigos")
  Set<String> findCodigosExistentes(Collection<String> codigos);

  /**
   * Activas cuyo código o nombre contiene el patrón, sin distinguir mayúsculas ni tildes. El patrón
   * llega en minúsculas, sin tildes y con los comodines escapados con {@code !}.
   */
  @Query(
      """
      select i from Instalacion i
      where i.activa = true
        and (cast(function('translate', lower(i.codigo), 'áéíóúüñ', 'aeiouun') as String)
              like :patron escape '!'
          or cast(function('translate', lower(i.nombre), 'áéíóúüñ', 'aeiouun') as String)
              like :patron escape '!')
      """)
  Page<Instalacion> buscarActivas(String patron, Pageable pageable);
}
