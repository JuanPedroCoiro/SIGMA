package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.shared.api.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tablero de pendientes (CU-9, HU-04) para la Jefatura y el Operador. */
@RestController
@RequestMapping("/api/v1/pendientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('JEFATURA', 'OPERADOR')")
class PendientesController {

  private final PendientesService pendientesService;

  /**
   * OTs Pendiente, Asignada, En proceso y En pausa, paginadas. Por defecto, por vencimiento; {@code
   * orden=PRIORIDAD} o {@code orden=CREACION} para los órdenes alternativos (RF-17).
   */
  @GetMapping("/ordenes")
  PageResponse<OrdenPendiente> ordenes(
      @RequestParam(defaultValue = "VENCIMIENTO") CriterioOrden orden,
      @PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.de(pendientesService.ordenes(orden, pageable));
  }

  /** Incidentes en espera de tercero: van aparte porque no tienen vencimiento. */
  @GetMapping("/incidentes-en-espera")
  List<IncidenteEnEspera> incidentesEnEspera() {
    return pendientesService.incidentesEnEspera();
  }
}
