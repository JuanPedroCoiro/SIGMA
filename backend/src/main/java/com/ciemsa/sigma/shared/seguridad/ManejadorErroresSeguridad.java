package com.ciemsa.sigma.shared.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Los errores de Spring Security (sin sesión, sin permiso, login fallido) ocurren en los filtros,
 * antes de llegar a un controller. Esta clase los reenvía al GlobalExceptionHandler para que
 * respondan con el mismo ProblemDetail que el resto de la API.
 */
@Component
public class ManejadorErroresSeguridad
    implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

  private final HandlerExceptionResolver resolver;

  public ManejadorErroresSeguridad(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  /** Pedido sin sesión (o con la sesión vencida) a una ruta protegida: 401. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    resolver.resolveException(request, response, null, ex);
  }

  /** Pedido con sesión pero sin permiso, o sin token CSRF: 403. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
    resolver.resolveException(request, response, null, ex);
  }

  /** Login fallido: credenciales incorrectas, cuenta desactivada o bloqueada (CU-12). */
  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    resolver.resolveException(request, response, null, ex);
  }
}
