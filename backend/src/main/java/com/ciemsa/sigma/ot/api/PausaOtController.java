package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.PausaOtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Detalle de la OT y sus pausas. Técnico (solo OTs de su cuadrilla, lo verifica el servicio),
 * Operador y Jefatura. Las demás acciones sobre /ordenes van en sus propios controladores.
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
public class PausaOtController {

  private final PausaOtService pausaOtService;

  @GetMapping("/{id}")
  public OrdenTrabajoDetalle obtener(@PathVariable Long id) {
    return OrdenTrabajoDetalle.de(pausaOtService.obtener(id));
  }

  @PostMapping("/{id}/pausar")
  public OrdenTrabajoDetalle pausar(
      @PathVariable Long id, @Valid @RequestBody PausarOtRequest req) {
    return OrdenTrabajoDetalle.de(pausaOtService.pausar(id, req.motivo(), req.detalle()));
  }

  @PostMapping("/{id}/reanudar")
  public OrdenTrabajoDetalle reanudar(@PathVariable Long id) {
    return OrdenTrabajoDetalle.de(pausaOtService.reanudar(id));
  }
}
