package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.AsignacionOtService;
import com.ciemsa.sigma.ot.application.CargaCuadrilla;
import com.ciemsa.sigma.ot.application.OrdenTrabajoConsultaService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Asignar o reasignar OTs a una cuadrilla (CU-2, HU-05): solo la Jefatura. */
@RestController
@RequestMapping("/api/v1/ordenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class AsignacionOtController {

  private final AsignacionOtService asignacionOtService;
  private final OrdenTrabajoConsultaService ordenTrabajoConsultaService;

  /**
   * Todas o ninguna: si una OT no se puede asignar, 400 con su número. Fecha anterior a hoy, 400
   * /errores/fecha-anterior (2a).
   */
  @PostMapping("/asignar")
  public List<OrdenTrabajoDetalle> asignar(@Valid @RequestBody AsignarOtsRequest req) {
    return asignacionOtService
        .asignar(req.idsOrdenTrabajo(), req.idCuadrilla(), req.fechaEjecucion())
        .stream()
        .map(OrdenTrabajoDetalle::de)
        .toList();
  }

  /** Criterio 3: cuántas OTs tiene cada cuadrilla ese día; las que no figuran, ninguna. */
  @GetMapping("/carga-por-cuadrilla")
  public List<CargaCuadrilla> cargaPorCuadrilla(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
    return ordenTrabajoConsultaService.cargaPorCuadrilla(fecha);
  }
}
