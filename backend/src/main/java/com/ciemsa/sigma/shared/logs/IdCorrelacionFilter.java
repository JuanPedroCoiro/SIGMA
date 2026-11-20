package com.ciemsa.sigma.shared.logs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le da a cada pedido un id de correlación (#95): va en todas las líneas de log del pedido y en la
 * cabecera de la respuesta, para encontrar en los logs lo que pasó cuando alguien reporta un error.
 * Si el proxy ya mandó uno válido, se reutiliza.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdCorrelacionFilter extends OncePerRequestFilter {

  public static final String CABECERA = "X-Request-Id";
  public static final String MDC_ID_CORRELACION = "idCorrelacion";

  /** Solo letras, números y guiones: un valor externo no puede inyectar texto en los logs. */
  private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String id = idDelPedido(request);
    MDC.put(MDC_ID_CORRELACION, id);
    response.setHeader(CABECERA, id);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_ID_CORRELACION);
    }
  }

  private static String idDelPedido(HttpServletRequest request) {
    String recibido = request.getHeader(CABECERA);
    return recibido != null && VALIDO.matcher(recibido).matches()
        ? recibido
        : UUID.randomUUID().toString();
  }
}
