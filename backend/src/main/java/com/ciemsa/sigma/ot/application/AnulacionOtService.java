package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anular una OT que no corresponde ejecutar (CU-19, HU-04). Desde qué estados se puede lo decide
 * EstadoOT; si estaba en pausa, la pausa se cierra con quien anula.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AnulacionOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;

  public DetalleOt anular(Long idOrdenTrabajo, String motivo) {
    OrdenTrabajo ot =
        ordenTrabajoRepository
            .findById(idOrdenTrabajo)
            .orElseThrow(
                () ->
                    new NoEncontradoException(
                        "No existe la orden de trabajo " + idOrdenTrabajo + "."));
    Instant ahora = Instant.now(clock);
    ot.anular(motivo, usuarioActual.id(), ahora);
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos().forEach(eventos::publishEvent);
    return DetalleOt.de(ot, ahora);
  }
}
