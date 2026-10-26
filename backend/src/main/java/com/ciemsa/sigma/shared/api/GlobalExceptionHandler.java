package com.ciemsa.sigma.shared.api;

import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
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

/** Traduce las excepciones a ProblemDetail (RFC 9457). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(NoEncontradoException.class)
  ProblemDetail noEncontrado(NoEncontradoException ex) {
    return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
  }

  @ExceptionHandler(ConflictoException.class)
  ProblemDetail conflicto(ConflictoException ex) {
    return problema(HttpStatus.CONFLICT, "Conflicto con el estado actual", ex.getMessage());
  }

  @ExceptionHandler(ReglaNegocioException.class)
  ProblemDetail reglaNegocio(ReglaNegocioException ex) {
    return problema(
        HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio no cumplida", ex.getMessage());
  }

  /** Dos usuarios editaron el mismo registro: el segundo no pisa los cambios del primero. */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  ProblemDetail edicionConcurrente(ObjectOptimisticLockingFailureException ex) {
    return problema(
        HttpStatus.CONFLICT,
        "Edición concurrente",
        "Otro usuario modificó estos datos. Recargá la pantalla y volvé a intentar.");
  }

  /** {@code @PreAuthorize} rechazó el pedido: el usuario no tiene el rol necesario. */
  @ExceptionHandler(AccessDeniedException.class)
  ProblemDetail accesoDenegado(AccessDeniedException ex) {
    return problema(
        HttpStatus.FORBIDDEN, "Acceso denegado", "No tenés permiso para realizar esta operación.");
  }

  /** Sin sesión o con la sesión vencida (CU-12 *a): hay que volver a iniciar sesión. */
  @ExceptionHandler(AuthenticationException.class)
  ProblemDetail noAutenticado(AuthenticationException ex) {
    return problemaIngreso(
        "SESION_REQUERIDA",
        "No autenticado",
        "Tu sesión venció o no iniciaste sesión. Iniciá sesión para continuar.");
  }

  /** CU-12 4a: el mismo mensaje si el correo no existe o la contraseña es incorrecta. */
  @ExceptionHandler(BadCredentialsException.class)
  ProblemDetail credencialesIncorrectas(BadCredentialsException ex) {
    return problemaIngreso(
        "CREDENCIALES_INCORRECTAS",
        "Credenciales incorrectas",
        "El correo o la contraseña no son correctos.");
  }

  /** CU-12 4b: la cuenta fue dada de baja. */
  @ExceptionHandler(DisabledException.class)
  ProblemDetail cuentaDesactivada(DisabledException ex) {
    return problemaIngreso(
        "CUENTA_DESACTIVADA",
        "Cuenta desactivada",
        "Tu cuenta está desactivada. Contactá a la Jefatura.");
  }

  /** CU-12 4c: bloqueo temporal por intentos fallidos consecutivos. */
  @ExceptionHandler(LockedException.class)
  ProblemDetail cuentaBloqueada(LockedException ex) {
    return problemaIngreso(
        "CUENTA_BLOQUEADA",
        "Cuenta bloqueada",
        "Tu cuenta quedó bloqueada por varios intentos fallidos. Volvé a intentar en unos minutos.");
  }

  /** Falta el token CSRF o no coincide con el de la sesión. */
  @ExceptionHandler(CsrfException.class)
  ProblemDetail csrfInvalido(CsrfException ex) {
    return problema(
        HttpStatus.FORBIDDEN,
        "Token CSRF inválido",
        "La sesión de seguridad no es válida. Recargá la página y volvé a intentar.");
  }

  /** ?sort con un campo que no existe en la entidad. */
  @ExceptionHandler(PropertyReferenceException.class)
  ProblemDetail ordenInvalido(PropertyReferenceException ex) {
    return problema(
        HttpStatus.BAD_REQUEST,
        "Orden inválido",
        "No se puede ordenar por '" + ex.getPropertyName() + "'.");
  }

  /** Último recurso: 500 sin exponer detalles internos; el detalle queda en el log. */
  @ExceptionHandler(Exception.class)
  ProblemDetail inesperado(Exception ex) {
    log.error("Error no controlado", ex);
    return problema(
        HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado.");
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

  private static ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
    problema.setTitle(titulo);
    return problema;
  }

  /** Errores de ingreso: siempre 401, con un código para que el frontend sepa qué mostrar. */
  private static ProblemDetail problemaIngreso(String codigo, String titulo, String detalle) {
    ProblemDetail problema = problema(HttpStatus.UNAUTHORIZED, titulo, detalle);
    problema.setProperty("codigo", codigo);
    return problema;
  }

  public record ErrorCampo(String campo, String mensaje) {}
}
