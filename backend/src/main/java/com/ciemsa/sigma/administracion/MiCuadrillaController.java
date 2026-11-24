package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CambiarCuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.MiCuadrillaResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuadrilla del técnico (HU-20): solo el Técnico, y siempre sobre su propia cuenta. La Jefatura
 * la cambia desde la edición del usuario (CU-13b).
 */
@RestController
@RequestMapping("/api/v1/mi-cuadrilla")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TECNICO')")
class MiCuadrillaController {

  private final MiCuadrillaService miCuadrillaService;

  /** La actual, cuántas OTs dejaría de ver al cambiarse y las cuadrillas activas. */
  @GetMapping
  MiCuadrillaResponse consultar() {
    return miCuadrillaService.consultar();
  }

  /** Se suma a otra cuadrilla activa, o vuelve a la suya. Responde como el GET, ya actualizado. */
  @PostMapping("/cambiar")
  MiCuadrillaResponse cambiar(@Valid @RequestBody CambiarCuadrillaRequest req) {
    return miCuadrillaService.cambiar(req.idCuadrilla());
  }
}
