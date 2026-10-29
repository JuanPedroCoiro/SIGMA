package com.ciemsa.sigma.administracion.dto;

import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.shared.seguridad.Rol;

/** Datos de un usuario para el listado y el detalle (CU-13c). Nunca incluye el hash. */
public record UsuarioResponse(
    Long id,
    String nombre,
    String correo,
    Rol rol,
    Long idCuadrilla,
    String nombreCuadrilla,
    boolean activo) {

  /** Llamarlo dentro de la transacción del servicio: la cuadrilla se carga de forma diferida. */
  public static UsuarioResponse de(Usuario usuario) {
    Cuadrilla cuadrilla = usuario.getCuadrilla();
    return new UsuarioResponse(
        usuario.getId(),
        usuario.getNombre(),
        usuario.getCorreo(),
        usuario.getRol(),
        cuadrilla == null ? null : cuadrilla.getId(),
        cuadrilla == null ? null : cuadrilla.getNombre(),
        usuario.isActivo());
  }
}
