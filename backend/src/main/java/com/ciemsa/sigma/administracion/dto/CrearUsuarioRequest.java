package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.shared.seguridad.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de usuario (CU-13a). La cuadrilla solo se indica para los técnicos. */
public record CrearUsuarioRequest(
    @NotBlank(message = "Ingresá el nombre.")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres.")
        String nombre,
    @NotBlank(message = "Ingresá el correo.")
        @Email(message = "Ingresá un correo válido, por ejemplo nombre@ciemsa.com.")
        @Size(max = 255, message = "El correo admite hasta 255 caracteres.")
        String correo,
    @NotBlank(message = "Ingresá la contraseña inicial.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String contrasena,
    @NotNull(message = "Elegí el rol.") Rol rol,
    Long idCuadrilla) {}
