package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.IncidenteService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registro de incidentes (CU-1, HU-03): Operador y Jefatura, verificado en cada pedido. */
@RestController
@RequestMapping("/api/v1/incidentes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPERADOR','JEFATURA')")
public class IncidenteController {

  private final IncidenteService incidenteService;

  @PostMapping
  public ResponseEntity<IncidenteResponse> registrar(
      @Valid @RequestBody RegistrarIncidenteRequest req) {
    IncidenteResponse incidente = IncidenteResponse.de(incidenteService.registrar(req.aCommand()));
    return ResponseEntity.created(URI.create("/api/v1/incidentes/" + incidente.id()))
        .body(incidente);
  }

  @GetMapping("/{id}")
  public IncidenteResponse obtener(@PathVariable Long id) {
    return IncidenteResponse.de(incidenteService.obtener(id));
  }

  /** Resuelve la espera de un tercero sin OT: volvió a comunicar (4b). */
  @PostMapping("/{id}/cerrar-sin-ot")
  public IncidenteResponse cerrarSinOt(@PathVariable Long id) {
    return IncidenteResponse.de(incidenteService.cerrarSinOt(id));
  }

  /** Resuelve la espera de un tercero con una OT: el SLA se cuenta desde ahora (4b). */
  @PostMapping("/{id}/generar-ot")
  public IncidenteResponse generarOt(@PathVariable Long id) {
    return IncidenteResponse.de(incidenteService.generarOt(id));
  }
}
