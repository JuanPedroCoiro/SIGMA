package com.ciemsa.sigma.pendientes;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resumen de la página de inicio (#72): cualquier usuario con sesión, según su rol. */
@RestController
@RequestMapping("/api/v1/inicio")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
class InicioController {

  private final ResumenInicioService resumenInicioService;

  @GetMapping("/resumen")
  ResumenInicio resumen() {
    return resumenInicioService.resumen();
  }
}
