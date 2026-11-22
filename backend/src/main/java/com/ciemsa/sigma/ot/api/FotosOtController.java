package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.ArchivoFoto;
import com.ciemsa.sigma.ot.application.FotosCierreService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fotos del cierre técnico de una OT (#90): las ve quien puede ver la OT (el técnico, solo las de
 * su cuadrilla). Se suben con el cierre, en EjecucionOtController.
 */
@RestController
@RequestMapping("/api/v1/ordenes/{idOrdenTrabajo}/fotos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
public class FotosOtController {

  private final FotosCierreService fotosCierreService;

  @GetMapping
  public List<FotoCierreResponse> listar(@PathVariable Long idOrdenTrabajo) {
    return fotosCierreService.listar(idOrdenTrabajo).stream().map(FotoCierreResponse::de).toList();
  }

  /** La imagen con su tipo real. Caché solo del navegador: son datos del sistema, no públicos. */
  @GetMapping("/{idFoto}")
  public ResponseEntity<byte[]> ver(@PathVariable Long idOrdenTrabajo, @PathVariable Long idFoto) {
    ArchivoFoto archivo = fotosCierreService.leer(idOrdenTrabajo, idFoto);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(archivo.adjunto().getTipo().mime()))
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
        .body(archivo.contenido());
  }
}
