package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de cuadrillas. En el MVP se cargan por script y no tienen ABM. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CuadrillaService {

  private final CuadrillaRepository cuadrillaRepository;

  /** Cuadrillas activas para los desplegables (catálogo chico, sin paginar). */
  public List<CuadrillaResponse> listarActivas() {
    return cuadrillaRepository.findByActivaTrueOrderByNombreAsc().stream()
        .map(CuadrillaResponse::de)
        .toList();
  }
}
