package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.shared.seguridad.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Modificación de usuario (CU-13b). La contraseña se cambia aparte. */
public record ModificarUsuarioRequest(
    @NotBlank(message = "Ingresá el nombre.")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres.")
        String nombre,
    @NotBlank(message = "Ingresá el correo.")
        @Email(message = "Ingresá un correo válido, por ejemplo nombre@ciemsa.com.")
        @Size(max = 255, message = "El correo admite hasta 255 caracteres.")
        String correo,
    @NotNull(message = "Elegí el rol.") Rol rol,
    Long idCuadrilla) {}
