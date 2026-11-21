package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.CategoriaFalla;
import com.ciemsa.sigma.ot.infrastructure.CategoriaFallaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de categorías de falla para registrar un incidente (CU-1, HU-03). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoriaFallaService {

  private final CategoriaFallaRepository categoriaFallaRepository;

  /** Las activas, ordenadas por nombre. Catálogo chico: sin paginar. */
  public List<CategoriaFalla> listarActivas() {
    return categoriaFallaRepository.findByActivaTrueOrderByNombreAsc();
  }
}
