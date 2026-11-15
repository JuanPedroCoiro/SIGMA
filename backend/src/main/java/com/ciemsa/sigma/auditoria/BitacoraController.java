package com.ciemsa.sigma.auditoria;

import com.ciemsa.sigma.auditoria.dto.RegistroAuditoriaResponse;
import com.ciemsa.sigma.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de la bitácora (HU-12): solo la Jefatura. */
@RestController
@RequestMapping("/api/v1/bitacora")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class BitacoraController {

  private final AuditoriaService auditoriaService;

  /** Paginado, más reciente primero; filtros opcionales por entidad e id de la entidad. */
  @GetMapping
  public PageResponse<RegistroAuditoriaResponse> listar(
      @RequestParam(required = false) EntidadAuditada entidad,
      @RequestParam(required = false) Long idEntidad,
      @PageableDefault(
              size = 20,
              sort = {"fechaHora", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    return PageResponse.de(auditoriaService.listar(entidad, idEntidad, pageable));
  }
}
