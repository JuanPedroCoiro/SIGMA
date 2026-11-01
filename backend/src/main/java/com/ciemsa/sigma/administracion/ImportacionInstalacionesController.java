package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.ResultadoImportacion;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Importación de instalaciones desde una planilla CSV (HU-02, RF-29). */
@RestController
@RequestMapping("/api/v1/instalaciones")
@RequiredArgsConstructor
class ImportacionInstalacionesController {

  private static final String TIPO_PLANILLA_INVALIDA = "/errores/planilla-invalida";

  private final ImportacionInstalacionesService importacionService;

  /** 200 con la cantidad importada, o 422 con los errores por fila (y nada importado). */
  @PostMapping(path = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('JEFATURA')")
  ResponseEntity<?> importar(@RequestParam("archivo") MultipartFile archivo) throws IOException {
    ResultadoImportacion resultado;
    try (InputStream contenido = archivo.getInputStream()) {
      resultado = importacionService.importar(contenido);
    }
    if (!resultado.tieneErrores()) {
      return ResponseEntity.ok(resultado);
    }
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "La planilla tiene "
                + resultado.errores().size()
                + " errores. No se importó ninguna instalación: corregí la planilla y volvé a"
                + " subirla.");
    problema.setType(URI.create(TIPO_PLANILLA_INVALIDA));
    problema.setTitle("Planilla con errores");
    problema.setProperty("errores", resultado.errores());
    return ResponseEntity.of(problema).build();
  }

  /**
   * 422 si el archivo entero no sirve (vacío, sin filas o sin columnas): no hay errores por fila.
   */
  @ExceptionHandler(PlanillaInvalidaException.class)
  ProblemDetail planillaInvalida(PlanillaInvalidaException ex) {
    ProblemDetail problema =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    problema.setType(URI.create("/errores/" + ex.getTipo()));
    problema.setTitle("Planilla inválida");
    problema.setProperty("errores", List.of());
    return problema;
  }
}
