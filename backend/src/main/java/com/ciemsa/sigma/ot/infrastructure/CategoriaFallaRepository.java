package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.CategoriaFalla;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaFallaRepository extends JpaRepository<CategoriaFalla, Long> {

  List<CategoriaFalla> findByActivaTrueOrderByNombreAsc();
}
