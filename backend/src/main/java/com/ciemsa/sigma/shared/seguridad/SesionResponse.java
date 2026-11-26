package com.ciemsa.sigma.shared.seguridad;

/** Usuario en sesión, para que el frontend sepa quién ingresó y qué menú mostrarle. */
public record SesionResponse(Long id, String nombre, String correo, Rol rol, Long idCuadrilla) {

  /** {@code idCuadrilla} es la actual, leída de la base, y no la que tenía al ingresar. */
  public static SesionResponse de(UsuarioAutenticado usuario, Long idCuadrilla) {
    return new SesionResponse(
        usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol(), idCuadrilla);
  }
}
