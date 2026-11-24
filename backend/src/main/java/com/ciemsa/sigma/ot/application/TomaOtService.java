package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El técnico toma una OT pendiente para su cuadrilla o la libera (HU-20, CU-2 1a). Desde qué
 * estados se puede lo decide EstadoOT. La OT se bloquea: si dos técnicos la toman a la vez, el
 * segundo la encuentra Asignada y se rechaza sin quitársela al primero; si la Jefatura la asignó
 * con una versión vieja, falla su guardado (control de versión).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TomaOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final AccesoOt accesoOt;
  private final UsuarioService usuarioService;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ZoneId zonaHoraria;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;

  /**
   * Pendiente → Asignada a la cuadrilla actual del técnico, con la fecha de hoy en Montevideo. No
   * pasa por {@link AccesoOt}: una OT pendiente no es de ninguna cuadrilla.
   */
  public DetalleOt tomar(Long idOrdenTrabajo) {
    Long idCuadrilla =
        usuarioService
            .idCuadrillaDe(usuarioActual.id())
            .orElseThrow(
                () ->
                    new ReglaNegocioException(
                        "No integrás ninguna cuadrilla: pedile a la Jefatura que te asigne a una."));
    OrdenTrabajo ot =
        ordenTrabajoRepository
            .findConBloqueoById(idOrdenTrabajo)
            .orElseThrow(
                () ->
                    new NoEncontradoException(
                        "No existe la orden de trabajo " + idOrdenTrabajo + "."));
    Instant ahora = Instant.now(clock);
    ot.tomar(idCuadrilla, LocalDate.ofInstant(ahora, zonaHoraria), ahora);
    return guardarYPublicar(ot, ahora);
  }

  /** Asignada o En proceso → Pendiente sin asignar. Solo la cuadrilla que la tiene (403 si no). */
  public DetalleOt liberar(Long idOrdenTrabajo) {
    OrdenTrabajo ot = accesoOt.buscarParaModificar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.liberar(ahora);
    return guardarYPublicar(ot, ahora);
  }

  private DetalleOt guardarYPublicar(OrdenTrabajo ot, Instant ahora) {
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos(usuarioActual.id()).forEach(eventos::publishEvent);
    return armadorDetalleOt.armar(ot, ahora);
  }
}
