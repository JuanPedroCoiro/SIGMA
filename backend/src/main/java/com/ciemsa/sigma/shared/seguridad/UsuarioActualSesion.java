package com.ciemsa.sigma.shared.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Obtiene el usuario actual de la sesión de Spring Security. */
@Component
class UsuarioActualSesion implements UsuarioActual {

  @Override
  public Long id() {
    return autenticado().getId();
  }

  @Override
  public Rol rol() {
    return autenticado().getRol();
  }

  private UsuarioAutenticado autenticado() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof UsuarioAutenticado usuario)) {
      throw new IllegalStateException("No hay un usuario autenticado en este pedido.");
    }
    return usuario;
  }
}
