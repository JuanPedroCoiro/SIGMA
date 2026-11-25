package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.ValidacionOtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Validar u observar una OT cerrada técnicamente (HU-09, CU-5): solo la Jefatura. Desde otro estado
 * que no sea Cerrada técnica, 400 /errores/transicion-invalida con {@code estadoActual}.
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class ValidacionOtController {

  private final ValidacionOtService validacionOtService;

  /**
   * Cerrada técnica → Validada. 3a: si al último cierre le falta la foto Antes o la Después, 409
   * /errores/fotos-faltantes con {@code momentosFaltantes}; se confirma con {@code aceptarSinFotos:
   * true}.
   */
  @PostMapping("/{id}/validar")
  public OrdenTrabajoDetalle validar(
      @PathVariable Long id, @RequestBody(required = false) ValidarOtRequest req) {
    boolean aceptarSinFotos = req != null && req.aceptarSinFotos();
    return OrdenTrabajoDetalle.de(validacionOtService.validar(id, aceptarSinFotos));
  }

  /** 4a: Cerrada técnica → Asignada, a la misma cuadrilla, con el motivo. */
  @PostMapping("/{id}/observar")
  public OrdenTrabajoDetalle observar(
      @PathVariable Long id, @Valid @RequestBody ObservarOtRequest req) {
    return OrdenTrabajoDetalle.de(validacionOtService.observar(id, req.motivo()));
  }
}
