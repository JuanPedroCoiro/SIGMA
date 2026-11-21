package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.AnulacionOtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Anular una OT (CU-19): solo la Jefatura. */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class AnulacionOtController {

  private final AnulacionOtService anulacionOtService;

  /**
   * Desde Pendiente o Asignada, o En pausa si antes estaba en uno de esos. Desde En proceso o
   * posteriores, 400 /errores/transicion-invalida (5a).
   */
  @PostMapping("/{id}/anular")
  public OrdenTrabajoDetalle anular(
      @PathVariable Long id, @Valid @RequestBody AnularOtRequest req) {
    return OrdenTrabajoDetalle.de(anulacionOtService.anular(id, req.motivo()));
  }
}
