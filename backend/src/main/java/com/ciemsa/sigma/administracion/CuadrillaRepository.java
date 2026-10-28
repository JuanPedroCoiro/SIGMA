package com.ciemsa.sigma.administracion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, Long> {

  List<Cuadrilla> findByActivaTrueOrderByNombreAsc();
}
