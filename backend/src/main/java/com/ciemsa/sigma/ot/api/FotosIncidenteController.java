package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.ArchivoFoto;
import com.ciemsa.sigma.ot.application.FotoNueva;
import com.ciemsa.sigma.ot.application.FotosIncidenteService;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Fotos de un incidente (#71). Suben Operador y Jefatura, al registrarlo o después (#85). Las ven
 * ellos y el técnico de la cuadrilla que tiene la OT del incidente: la cuadrilla la controla el
 * servicio (AccesoOt).
 */
@RestController
@RequestMapping("/api/v1/incidentes/{idIncidente}/fotos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPERADOR','JEFATURA')")
public class FotosIncidenteController {

  private final FotosIncidenteService fotosIncidenteService;

  /** Una o más fotos en el campo «fotos» (multipart). 201 con las que se subieron. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public List<FotoResponse> subir(
      @PathVariable Long idIncidente, @RequestParam("fotos") List<MultipartFile> fotos)
      throws IOException {
    List<FotoNueva> nuevas = new ArrayList<>();
    for (MultipartFile foto : fotos) {
      nuevas.add(new FotoNueva(foto.getOriginalFilename(), foto.getBytes()));
    }
    return fotosIncidenteService.subir(idIncidente, nuevas).stream().map(FotoResponse::de).toList();
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
  public List<FotoResponse> listar(@PathVariable Long idIncidente) {
    return fotosIncidenteService.listar(idIncidente).stream().map(FotoResponse::de).toList();
  }

  /** La imagen con su tipo real. Caché solo del navegador: son datos del sistema, no públicos. */
  @GetMapping("/{idFoto}")
  @PreAuthorize("hasAnyRole('TECNICO','OPERADOR','JEFATURA')")
  public ResponseEntity<byte[]> ver(@PathVariable Long idIncidente, @PathVariable Long idFoto) {
    ArchivoFoto archivo = fotosIncidenteService.leer(idIncidente, idFoto);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(archivo.adjunto().getTipo().mime()))
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
        .body(archivo.contenido());
  }
}
