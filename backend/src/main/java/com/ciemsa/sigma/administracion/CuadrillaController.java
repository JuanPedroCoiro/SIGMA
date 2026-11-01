package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de cuadrillas (RF-23): solo la Jefatura, verificado en cada pedido. */
@RestController
@RequestMapping("/api/v1/cuadrillas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class CuadrillaController {

  private final CuadrillaService cuadrillaService;

  @GetMapping
  public List<CuadrillaResponse> listar() {
    return cuadrillaService.listar();
  }

  @PostMapping
  public ResponseEntity<CuadrillaResponse> crear(@Valid @RequestBody CuadrillaRequest req) {
    CuadrillaResponse cuadrilla = cuadrillaService.crear(req);
    return ResponseEntity.created(URI.create("/api/v1/cuadrillas/" + cuadrilla.id()))
        .body(cuadrilla);
  }

  @PutMapping("/{id}")
  public CuadrillaResponse modificar(
      @PathVariable Long id, @Valid @RequestBody CuadrillaRequest req) {
    return cuadrillaService.modificar(id, req);
  }

  /** Baja lógica: las cuadrillas no se borran. */
  @PostMapping("/{id}/desactivar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void desactivar(@PathVariable Long id) {
    cuadrillaService.desactivar(id);
  }

  @PostMapping("/{id}/activar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void activar(@PathVariable Long id) {
    cuadrillaService.activar(id);
  }
}
