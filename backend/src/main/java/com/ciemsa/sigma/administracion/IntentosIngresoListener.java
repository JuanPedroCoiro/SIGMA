package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lleva la cuenta de los intentos de ingreso para el bloqueo temporal (CU-12 4c) y deja en el log
 * técnico (#95) el bloqueo y los ingresos. Solo ids: nunca el correo ni la contraseña.
 */
@Slf4j
@Component
public class IntentosIngresoListener {

  private final UsuarioRepository usuarioRepository;
  private final Clock clock;
  private final Duration duracionBloqueo;

  public IntentosIngresoListener(
      UsuarioRepository usuarioRepository,
      Clock clock,
      @Value("${sigma.seguridad.duracion-bloqueo}") Duration duracionBloqueo) {
    this.usuarioRepository = usuarioRepository;
    this.clock = clock;
    this.duracionBloqueo = duracionBloqueo;
  }

  /**
   * Contraseña incorrecta: suma un intento; al quinto, la cuenta se bloquea. El WARN sale una sola
   * vez, cuando la cuenta pasa a bloqueada, y no en cada intento mientras dura el bloqueo.
   */
  @EventListener
  @Transactional
  public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
    String correo = Usuario.normalizarCorreo(evento.getAuthentication().getName());
    usuarioRepository
        .findByCorreo(correo)
        .ifPresentOrElse(
            usuario -> {
              Instant ahora = Instant.now(clock);
              boolean yaBloqueado = usuario.estaBloqueado(ahora);
              usuario.registrarIntentoFallido(ahora, duracionBloqueo);
              if (!yaBloqueado && usuario.estaBloqueado(ahora)) {
                log.warn(
                    "Usuario {} bloqueado hasta {} por intentos fallidos",
                    usuario.getId(),
                    usuario.getBloqueadoHasta());
              }
            },
            // Sin el correo: es un dato personal y puede ser un error de tipeo de otra cuenta.
            () -> log.warn("Intento de ingreso con un correo que no está registrado"));
  }

  /** Ingreso correcto: corta la racha de intentos fallidos. */
  @EventListener
  @Transactional
  public void alIngresar(AuthenticationSuccessEvent evento) {
    if (evento.getAuthentication().getPrincipal() instanceof UsuarioAutenticado autenticado) {
      usuarioRepository.findById(autenticado.getId()).ifPresent(Usuario::registrarIngresoExitoso);
      log.info("Ingreso del usuario {}", autenticado.getId());
    }
  }
}
