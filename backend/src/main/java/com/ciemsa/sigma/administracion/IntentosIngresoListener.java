package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lleva la cuenta de los intentos de ingreso para el bloqueo temporal (CU-12 4c). */
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

  /** Contraseña incorrecta: suma un intento; al quinto, la cuenta se bloquea. */
  @EventListener
  @Transactional
  public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
    String correo = Usuario.normalizarCorreo(evento.getAuthentication().getName());
    usuarioRepository
        .findByCorreo(correo)
        .ifPresent(usuario -> usuario.registrarIntentoFallido(Instant.now(clock), duracionBloqueo));
  }

  /** Ingreso correcto: corta la racha de intentos fallidos. */
  @EventListener
  @Transactional
  public void alIngresar(AuthenticationSuccessEvent evento) {
    if (evento.getAuthentication().getPrincipal() instanceof UsuarioAutenticado autenticado) {
      usuarioRepository.findById(autenticado.getId()).ifPresent(Usuario::registrarIngresoExitoso);
    }
  }
}
