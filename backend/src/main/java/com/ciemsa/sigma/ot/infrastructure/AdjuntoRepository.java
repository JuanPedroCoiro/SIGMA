package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.Adjunto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjuntoRepository extends JpaRepository<Adjunto, Long> {

  /** Las fotos del incidente, en el orden en que se subieron. */
  List<Adjunto> findByIdIncidenteOrderByCreadoEnAscIdAsc(Long idIncidente);

  long countByIdIncidente(Long idIncidente);

  /** Una foto, solo si es de ese incidente (así la URL no sirve para ver fotos de otro). */
  Optional<Adjunto> findByIdAndIdIncidente(Long id, Long idIncidente);
}
