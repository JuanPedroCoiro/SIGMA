package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de cuadrillas. En el MVP se cargan por script. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CuadrillaService {

  private final CuadrillaRepository cuadrillaRepository;

  /** Todas las cuadrillas con su estado, por nombre (catálogo chico, sin paginar). */
  public List<CuadrillaResponse> listar() {
    return cuadrillaRepository.findAll(Sort.by("nombre")).stream()
        .map(CuadrillaResponse::de)
        .toList();
  }
}
