package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.application.RegistrarIncidenteCommand;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de un incidente (CU-1). La prioridad es opcional: si no viene, se usa la sugerida por la
 * categoría (RN-01). Con {@code tercero}, el incidente queda en espera sin OT (4b).
 */
public record RegistrarIncidenteRequest(
    @NotNull(message = "Elegí la instalación.") Long idInstalacion,
    @NotNull(message = "Indicá el origen.") OrigenIncidente origen,
    @NotNull(message = "Elegí la categoría de la falla.") Long idCategoriaFalla,
    Prioridad prioridad,
    @NotBlank(message = "Describí la falla.")
        @Size(max = 2000, message = "La descripción admite hasta 2000 caracteres.")
        String descripcion,
    @Size(max = 150, message = "El tercero admite hasta 150 caracteres.") String tercero,
    @Size(max = 500, message = "La causa admite hasta 500 caracteres.") String causa) {

  RegistrarIncidenteCommand aCommand() {
    return new RegistrarIncidenteCommand(
        idInstalacion, origen, idCategoriaFalla, prioridad, descripcion, tercero, causa);
  }
}
