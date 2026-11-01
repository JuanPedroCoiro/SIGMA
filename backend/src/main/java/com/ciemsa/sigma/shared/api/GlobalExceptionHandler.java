package com.ciemsa.sigma.shared.api;

import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.CuentaBloqueadaException;
import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones a ProblemDetail (RFC 9457). Cada caso lleva su type (/errores/<tipo>),
 * según el contrato del login, para que el frontend elija el mensaje.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String ERRORES = "/errores/";

  // --- Negocio ---

  @ExceptionHandler(NoEncontradoException.class)
  ProblemDetail noEncontrado(NoEncontradoException ex) {
    return problema(HttpStatus.NOT_FOUND, ex.getTipo(), "Recurso no encontrado", ex.getMessage());
  }

  @ExceptionHandler(ConflictoException.class)
  ProblemDetail conflicto(ConflictoException ex) {
    return problema(
        HttpStatus.CONFLICT, ex.getTipo(), "Conflicto con el estado actual", ex.getMessage());
  }

  @ExceptionHandler(ReglaNegocioException.class)
  ProblemDetail reglaNegocio(ReglaNegocioException ex) {
    return problema(
        HttpStatus.BAD_REQUEST, ex.getTipo(), "Regla de negocio no cumplida", ex.getMessage());
  }

  /** Dos usuarios editaron el mismo registro: el segundo no pisa los cambios del primero. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail edicionConcurrente(ObjectOptimisticLockingFailureException ex) {
    return problema(
        HttpStatus.CONFLICT,
        "edicion-concurrente",
        "Edición concurrente",
        "Otro usuario modificó estos datos. Recargá la pantalla y volvé a intentar.");
  }

  // --- Seguridad (CU-12) ---

  /** Sin sesión o con la sesión vencida (*a): hay que volver a iniciar sesión. */
  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail noAutenticado(AuthenticationException ex) {
    return problema(
        HttpStatus.UNAUTHORIZED,
        "sin-sesion",
        "No autenticado",
        "Tu sesión venció o no iniciaste sesión. Iniciá sesión para continuar.");
  }

  /** 4a: el mismo mensaje si el correo no existe o la contraseña es incorrecta. */
  @ExceptionHandler(BadCredentialsException.class)
  ProblemDetail credencialesIncorrectas(BadCredentialsException ex) {
    return problema(
        HttpStatus.UNAUTHORIZED,
        "credenciales",
        "Credenciales incorrectas",
        "Correo o contraseña incorrectos.");
  }

  /** 4b: la cuenta fue desactivada. */
  @ExceptionHandler(DisabledException.class)
  ProblemDetail cuentaDesactivada(DisabledException ex) {
    return problema(
        HttpStatus.UNAUTHORIZED,
        "cuenta-desactivada",
        "Cuenta desactivada",
        "Tu cuenta está desactivada. Contactá a la Jefatura.");
  }

  /** 4c: bloqueo temporal por intentos fallidos consecutivos, con la hora en que se libera. */
  @ExceptionHandler(LockedException.class)
  ProblemDetail cuentaBloqueada(LockedException ex) {
    ProblemDetail problema =
        problema(
            HttpStatus.LOCKED,
            "usuario-bloqueado",
            "Usuario bloqueado",
            "Demasiados intentos fallidos.");
    if (ex instanceof CuentaBloqueadaException bloqueada) {
      problema.setProperty(
          "bloqueadoHasta",
          bloqueada.getBloqueadoHasta().truncatedTo(ChronoUnit.SECONDS).toString());
    }
    return problema;
  }

  /** {@code @PreAuthorize} rechazó el pedido: el usuario no tiene el rol necesario. */
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail accesoDenegado(AccessDeniedException ex) {
    return problema(
        HttpStatus.FORBIDDEN,
        "sin-permiso",
        "Acceso denegado",
        "No tenés permiso para realizar esta operación.");
  }

  /** Falta el token CSRF o no coincide con el de la sesión. */
  @ExceptionHandler(CsrfException.class)
  ProblemDetail csrfInvalido(CsrfException ex) {
    return problema(
        HttpStatus.FORBIDDEN,
        "csrf",
        "Token CSRF inválido",
        "La sesión de seguridad no es válida. Recargá la página y volvé a intentar.");
  }

  // --- Pedido inválido ---

  /** ?sort con un campo que no existe en la entidad. */
  @ExceptionHandler(PropertyReferenceException.class)
  ProblemDetail ordenInvalido(PropertyReferenceException ex) {
    return problema(
        HttpStatus.BAD_REQUEST,
        "validacion",
        "Orden inválido",
        "No se puede ordenar por '" + ex.getPropertyName() + "'.");
  }

  /** 400 con la lista de campos inválidos, para que el frontend marque cada uno. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ErrorCampo> errores =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage()))
            .toList();
    ProblemDetail body = ex.getBody();
    body.setTitle("Datos inválidos");
    body.setDetail("Revisá los campos marcados.");
    body.setProperty("errores", errores);
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /**
   * Los demás 400 que arma Spring (JSON mal formado, un rol que no existe, un id que no es número)
   * también son datos inválidos: les pone el type de validación. En Spring 7 un ProblemDetail sin
   * type definido devuelve null (equivale a about:blank).
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (body instanceof ProblemDetail problema
        && status.value() == HttpStatus.BAD_REQUEST.value()
        && problema.getType() == null) {
      problema.setType(URI.create(ERRORES + "validacion"));
    }
    return super.handleExceptionInternal(ex, body, headers, status, request);
  }

  // --- Último recurso ---

  /** 500 sin exponer detalles internos; el detalle queda en el log. */
  @ExceptionHandler(Exception.class)
  ProblemDetail inesperado(Exception ex) {
    log.error("Error no controlado", ex);
    return problema(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "interno",
        "Error interno",
        "Ocurrió un error inesperado.");
  }

  private static ProblemDetail problema(
      HttpStatus status, String tipo, String titulo, String detalle) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
    problema.setType(URI.create(ERRORES + tipo));
    problema.setTitle(titulo);
    return problema;
  }

  public record ErrorCampo(String campo, String mensaje) {}
}
