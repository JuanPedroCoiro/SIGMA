package com.ciemsa.sigma.shared.seguridad;

/** Usuario en sesión, para que el frontend sepa quién ingresó y qué menú mostrarle. */
public record SesionResponse(Long id, String nombre, String correo, Rol rol) {

  public static SesionResponse de(UsuarioAutenticado usuario) {
    return new SesionResponse(
        usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol());
  }
}
