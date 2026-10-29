package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de cuadrillas (RF-23): solo la Jefatura, verificado en cada pedido. */
@RestController
@RequestMapping("/api/v1/cuadrillas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class CuadrillaController {

  private final CuadrillaService cuadrillaService;

  @GetMapping
  public List<CuadrillaResponse> listar() {
    return cuadrillaService.listar();
  }
}
