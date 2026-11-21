package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.CategoriaFalla;
import com.ciemsa.sigma.ot.domain.Prioridad;

/** Una categoría de falla con la prioridad que propone (RN-01). */
public record CategoriaFallaResponse(Long id, String nombre, Prioridad prioridadSugerida) {

  static CategoriaFallaResponse de(CategoriaFalla categoria) {
    return new CategoriaFallaResponse(
        categoria.getId(), categoria.getNombre(), categoria.getPrioridadSugerida());
  }
}
