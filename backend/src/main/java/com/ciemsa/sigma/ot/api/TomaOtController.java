package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.TomaOtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tomar y liberar una OT (HU-20, CU-2 1a): solo el Técnico, para su cuadrilla actual. Tomar una OT
 * que ya no está pendiente es 400 /errores/transicion-invalida con {@code estadoActual}. Liberar
 * una OT que no es de su cuadrilla, incluida una pendiente (que no es de ninguna), es 403: {@code
 * AccesoOt} lo verifica antes que el estado. Liberar una de su cuadrilla que no está asignada ni en
 * proceso (en pausa, por ejemplo) es 400 con {@code estadoActual}.
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
public class TomaOtController {

  private final TomaOtService tomaOtService;

  /** Pendiente → Asignada a la cuadrilla del técnico, con la fecha de hoy. */
  @PostMapping("/{id}/tomar")
  public OrdenTrabajoDetalle tomar(@PathVariable Long id) {
    return OrdenTrabajoDetalle.de(tomaOtService.tomar(id));
  }

  /** Asignada o En proceso → Pendiente sin asignar; solo la cuadrilla que la tiene (403 si no). */
  @PostMapping("/{id}/liberar")
  public OrdenTrabajoDetalle liberar(@PathVariable Long id) {
    return OrdenTrabajoDetalle.de(tomaOtService.liberar(id));
  }
}
