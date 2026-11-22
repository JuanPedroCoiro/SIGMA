package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.EjecucionOtService;
import com.ciemsa.sigma.ot.application.FotoCierre;
import com.ciemsa.sigma.ot.application.FotoNueva;
import com.ciemsa.sigma.ot.domain.FotoInvalidaException;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
   * En proceso → Cerrada técnicamente, con sus fotos (HU-08). Multipart: «datos» (el JSON del
   * cierre), «fotos» y «momentos» (ANTES, DURANTE o DESPUES, uno por foto y en el mismo orden). Un
   * reintento con el mismo idOperacion no cierra de nuevo ni duplica las fotos.
   */
  @PostMapping(path = "/{id}/cerrar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public OrdenTrabajoDetalle cerrar(
      @PathVariable Long id,
      @Valid @RequestPart("datos") CerrarOtRequest req,
      @RequestPart(value = "fotos", required = false) List<MultipartFile> fotos,
      @RequestParam(value = "momentos", required = false) List<MomentoFoto> momentos)
      throws IOException {
    return OrdenTrabajoDetalle.de(
        ejecucionOtService.cerrar(
            id, req.idOperacion(), req.cierre(), fotosDelCierre(fotos, momentos)));
  }

  private static List<FotoCierre> fotosDelCierre(
      List<MultipartFile> fotos, List<MomentoFoto> momentos) throws IOException {
    List<MultipartFile> archivos = fotos == null ? List.of() : fotos;
    List<MomentoFoto> deCadaUna = momentos == null ? List.of() : momentos;
    if (archivos.size() != deCadaUna.size()) {
      throw new FotoInvalidaException(
          "Indicá el momento de cada foto: antes, durante o después del trabajo.");
    }
    List<FotoCierre> resultado = new ArrayList<>();
    for (int i = 0; i < archivos.size(); i++) {
      MultipartFile archivo = archivos.get(i);
      resultado.add(
          new FotoCierre(
              deCadaUna.get(i), new FotoNueva(archivo.getOriginalFilename(), archivo.getBytes())));
    }
    return resultado;
  }
}
