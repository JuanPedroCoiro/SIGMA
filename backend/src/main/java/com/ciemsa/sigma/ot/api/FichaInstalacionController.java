package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.FichaInstalacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ficha de la instalación (HU-10, CU-7) para todos los roles: el técnico la consulta antes de
 * intervenir (CU-3, paso 1). Está en ot porque cruza la instalación con sus OTs; el listado y el
 * mapa de instalaciones siguen en activos.
 */
@RestController
@RequestMapping("/api/v1/instalaciones/{idInstalacion}/ficha")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
public class FichaInstalacionController {

  private final FichaInstalacionService fichaInstalacionService;

  @GetMapping
  public FichaInstalacionResponse obtener(@PathVariable Long idInstalacion) {
    return FichaInstalacionResponse.de(fichaInstalacionService.obtener(idInstalacion));
  }
}
