package com.ciemsa.sigma.activos;

import com.ciemsa.sigma.activos.dto.InstalacionResponse;
import com.ciemsa.sigma.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de instalaciones (HU-02). La importación está en el módulo administracion. */
@RestController
@RequestMapping("/api/v1/instalaciones")
@RequiredArgsConstructor
class InstalacionController {

  private final InstalacionService instalacionService;

  /** Listado paginado, ordenado por código si no se indica otro orden. */
  @GetMapping
  @PreAuthorize("hasAnyRole('JEFATURA', 'OPERADOR')")
  PageResponse<InstalacionResponse> listar(
      @PageableDefault(size = 20, sort = "codigo") Pageable pageable) {
    return PageResponse.de(instalacionService.listar(pageable));
  }
}
