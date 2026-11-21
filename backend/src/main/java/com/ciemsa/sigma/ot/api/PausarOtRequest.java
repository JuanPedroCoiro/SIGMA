package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.MotivoPausa;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Pausa de una OT. El detalle es obligatorio con TERCERO (qué tercero) y OTRO: lo valida la OT. */
public record PausarOtRequest(
    @NotNull(message = "Elegí el motivo de la pausa.") MotivoPausa motivo,
    @Size(max = 500, message = "El detalle admite hasta 500 caracteres.") String detalle) {}
