package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Observación de una OT cerrada (HU-09, CU-5 4a): el motivo es obligatorio y lo ve la cuadrilla.
 */
public record ObservarOtRequest(
    @NotBlank(message = "Indicá qué hay que corregir.")
        @Size(
            max = OrdenTrabajo.LARGO_MAXIMO_OBSERVACION,
            message = "El motivo admite hasta 500 caracteres.")
        String motivo) {}
