package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;

/**
 * Datos para registrar un incidente (CU-1). {@code prioridad} null = la sugerida por la categoría;
 * {@code tercero} no vacío = falla de un tercero (4b).
 */
public record RegistrarIncidenteCommand(
    Long idInstalacion,
    OrigenIncidente origen,
    Long idCategoriaFalla,
    Prioridad prioridad,
    String descripcion,
    String tercero,
    String causa) {

  boolean esFallaDeTercero() {
    return tercero != null && !tercero.isBlank();
  }
}
