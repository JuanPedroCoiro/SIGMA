package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.CategoriaFallaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de categorías de falla (CU-1, HU-03): Operador y Jefatura. */
@RestController
@RequestMapping("/api/v1/categorias-falla")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPERADOR','JEFATURA')")
public class CategoriaFallaController {

  private final CategoriaFallaService categoriaFallaService;

  /** Solo las activas, ordenadas por nombre. */
  @GetMapping
  public List<CategoriaFallaResponse> listar() {
    return categoriaFallaService.listarActivas().stream().map(CategoriaFallaResponse::de).toList();
  }
}
