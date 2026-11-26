package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausar y reanudar una OT con motivo (precisiones del dominio del 01/11). Un técnico solo actúa
 * sobre las OTs asignadas a su cuadrilla actual; Operador y Jefatura, sobre cualquiera ({@link
 * AccesoOt}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PausaOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final AccesoOt accesoOt;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;

  public DetalleOt pausar(Long idOrdenTrabajo, MotivoPausa motivo, String detalle) {
    OrdenTrabajo ot = accesoOt.buscarParaOperar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.pausar(motivo, detalle, usuarioActual.id(), ahora);
    return guardarYPublicar(ot, ahora);
  }

  public DetalleOt reanudar(Long idOrdenTrabajo) {
    OrdenTrabajo ot = accesoOt.buscarParaOperar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.reanudar(usuarioActual.id(), ahora);
    return guardarYPublicar(ot, ahora);
  }

  @Transactional(readOnly = true)
  public DetalleOt obtener(Long idOrdenTrabajo) {
    return armadorDetalleOt.armar(accesoOt.buscar(idOrdenTrabajo), Instant.now(clock));
  }

  private DetalleOt guardarYPublicar(OrdenTrabajo ot, Instant ahora) {
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos(usuarioActual.id()).forEach(eventos::publishEvent);
    return armadorDetalleOt.armar(ot, ahora);
  }
}
