package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OTs que esperan la validación (HU-09, CU-5 paso 1): solo la Jefatura, que es quien valida. */
@RestController
@RequestMapping("/api/v1/pendientes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
class PorValidarController {

  private final PorValidarService porValidarService;

  /** Las Cerradas técnicamente, de la que espera hace más tiempo a la más nueva, paginadas. */
  @GetMapping("/por-validar")
  PageResponse<OrdenPorValidar> porValidar(@PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.de(porValidarService.ordenes(pageable));
  }
}
