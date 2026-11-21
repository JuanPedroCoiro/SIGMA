package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja «Mis OTs» (CU-3, HU-07): solo el Técnico. */
@RestController
@RequestMapping("/api/v1/mis-ots")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
class MisOtsController {

  private final MisOtsService misOtsService;

  /**
   * Las OTs Asignada, En proceso y En pausa de la cuadrilla del técnico, por fecha de ejecución y
   * vencimiento, paginadas.
   */
  @GetMapping
  PageResponse<OrdenDeMiCuadrilla> misOts(@PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.de(misOtsService.misOts(pageable));
  }
}
