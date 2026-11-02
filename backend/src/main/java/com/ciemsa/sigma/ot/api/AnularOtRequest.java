package com.ciemsa.sigma.ot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anulación de una OT (CU-19): el motivo es obligatorio (duplicada, improcedente u otro). */
public record AnularOtRequest(
    @NotBlank(message = "Indicá el motivo de la anulación.")
        @Size(max = 500, message = "El motivo admite hasta 500 caracteres.")
        String motivo) {}
