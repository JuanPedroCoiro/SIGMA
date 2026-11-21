package com.ciemsa.sigma.shared.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pone el id del usuario en sesión en el contexto de los logs (#95). Solo el id: el correo y el
 * nombre son datos personales (Ley 18.331).
 */
class UsuarioEnLogFilter extends OncePerRequestFilter {

  static final String MDC_ID_USUARIO = "idUsuario";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
      MDC.put(MDC_ID_USUARIO, String.valueOf(usuario.getId()));
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_ID_USUARIO);
    }
  }
}
