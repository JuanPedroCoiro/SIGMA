package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de cuadrillas: lo consulta cualquier usuario con sesión. */
@RestController
@RequestMapping("/api/v1/cuadrillas")
@RequiredArgsConstructor
public class CuadrillaController {

  private final CuadrillaService cuadrillaService;

  @GetMapping
  public List<CuadrillaResponse> listar() {
    return cuadrillaService.listarActivas();
  }
}
