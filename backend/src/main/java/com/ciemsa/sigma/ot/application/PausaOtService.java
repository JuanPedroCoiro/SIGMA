package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausar y reanudar una OT con motivo (precisiones del dominio del 01/11). Un técnico solo actúa
 * sobre las OTs asignadas a su cuadrilla actual; Operador y Jefatura, sobre cualquiera.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PausaOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final UsuarioService usuarioService;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;

  public DetalleOt pausar(Long idOrdenTrabajo, MotivoPausa motivo, String detalle) {
    OrdenTrabajo ot = buscarAccesible(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.pausar(motivo, detalle, usuarioActual.id(), ahora);
    return guardarYPublicar(ot, ahora);
  }

  public DetalleOt reanudar(Long idOrdenTrabajo) {
    OrdenTrabajo ot = buscarAccesible(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.reanudar(usuarioActual.id(), ahora);
    return guardarYPublicar(ot, ahora);
  }

  @Transactional(readOnly = true)
  public DetalleOt obtener(Long idOrdenTrabajo) {
    return armadorDetalleOt.armar(buscarAccesible(idOrdenTrabajo), Instant.now(clock));
  }

  private DetalleOt guardarYPublicar(OrdenTrabajo ot, Instant ahora) {
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos().forEach(eventos::publishEvent);
    return armadorDetalleOt.armar(ot, ahora);
  }

  /** 404 si no existe; 403 si es un técnico y la OT no está asignada a su cuadrilla actual. */
  private OrdenTrabajo buscarAccesible(Long idOrdenTrabajo) {
    OrdenTrabajo ot =
        ordenTrabajoRepository
            .findById(idOrdenTrabajo)
            .orElseThrow(
                () ->
                    new NoEncontradoException(
                        "No existe la orden de trabajo " + idOrdenTrabajo + "."));
    if (usuarioActual.rol() == Rol.TECNICO && !esDeSuCuadrilla(ot)) {
      throw new AccessDeniedException("La OT no está asignada a tu cuadrilla.");
    }
    return ot;
  }

  private boolean esDeSuCuadrilla(OrdenTrabajo ot) {
    return ot.getIdCuadrilla() != null
        && usuarioService
            .idCuadrillaDe(usuarioActual.id())
            .map(ot.getIdCuadrilla()::equals)
            .orElse(false);
  }
}
