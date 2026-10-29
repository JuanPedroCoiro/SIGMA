package com.ciemsa.sigma.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contraseña nueva asignada por la Jefatura (CU-13b). */
public record RestablecerContrasenaRequest(
    @NotBlank(message = "Ingresá la contraseña nueva.")
        @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.")
        String password) {}
