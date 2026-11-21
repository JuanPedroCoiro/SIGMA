package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.EjecucionOtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Iniciar y cerrar la OT (CU-3, HU-07): solo un técnico de la cuadrilla de la OT (lo verifica el
 * servicio). Una transición inválida es 400 /errores/transicion-invalida con {@code estadoActual}.
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
public class EjecucionOtController {

  private final EjecucionOtService ejecucionOtService;

  /** Asignada → En proceso. */
  @PostMapping("/{id}/iniciar")
  public OrdenTrabajoDetalle iniciar(@PathVariable Long id) {
    return OrdenTrabajoDetalle.de(ejecucionOtService.iniciar(id));
  }

  /**
   * En proceso → Cerrada técnicamente; un reintento con el mismo idOperacion no cierra de nuevo.
   */
  @PostMapping("/{id}/cerrar")
  public OrdenTrabajoDetalle cerrar(
      @PathVariable Long id, @Valid @RequestBody CerrarOtRequest req) {
    return OrdenTrabajoDetalle.de(ejecucionOtService.cerrar(id, req.idOperacion(), req.cierre()));
  }
}
