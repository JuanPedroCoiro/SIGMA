package com.ciemsa.sigma.administracion.eventos;

import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.shared.seguridad.Rol;

/**
 * Foto de los datos de una cuenta para la bitácora. No lleva la contraseña ni su hash, a propósito:
 * no hay forma de que lleguen a la bitácora.
 */
public record DatosUsuario(
    String nombre, String correo, Rol rol, Long idCuadrilla, boolean activo) {

  public static DatosUsuario de(Usuario usuario) {
    Cuadrilla cuadrilla = usuario.getCuadrilla();
    return new DatosUsuario(
        usuario.getNombre(),
        usuario.getCorreo(),
        usuario.getRol(),
        cuadrilla == null ? null : cuadrilla.getId(),
        usuario.isActivo());
  }
}
