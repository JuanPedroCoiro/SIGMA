package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.EntradaHistorial;
import com.ciemsa.sigma.ot.application.HistorialInstalacionService;
import com.ciemsa.sigma.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de la instalación (HU-10, CU-8) para todos los roles: OTs terminadas e incidentes sin
 * OT, paginados de lo más nuevo a lo más viejo. El orden es fijo: {@code sort} se ignora.
 */
@RestController
@RequestMapping("/api/v1/instalaciones/{idInstalacion}/historial")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
public class HistorialInstalacionController {

  private final HistorialInstalacionService historialInstalacionService;

  @GetMapping
  public PageResponse<EntradaHistorial> listar(
      @PathVariable Long idInstalacion, @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.de(historialInstalacionService.listar(idInstalacion, pageable));
  }
}
