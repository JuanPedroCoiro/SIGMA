package com.ciemsa.sigma.administracion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta y modificación de una cuadrilla. La zona es opcional. */
public record CuadrillaRequest(
    @NotBlank(message = "Ingresá el nombre.")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
        String nombre,
    @Size(max = 100, message = "La zona admite hasta 100 caracteres.") String zona) {}
