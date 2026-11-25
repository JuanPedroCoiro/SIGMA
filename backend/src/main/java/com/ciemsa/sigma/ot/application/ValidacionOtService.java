package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.FotosFaltantesException;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.AdjuntoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validar u observar una OT cerrada técnicamente (HU-09, CU-5): solo la Jefatura (lo exige el
 * controller). Desde qué estados se puede lo decide EstadoOT. La OT se bloquea, para que dos
 * personas no la validen y la observen a la vez.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ValidacionOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final AdjuntoRepository adjuntoRepository;
  private final AccesoOt accesoOt;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;

  /**
   * Cerrada técnicamente → Validada (paso 5). 3a: si al último cierre le falta la foto Antes o la
   * Después, 409 /errores/fotos-faltantes con {@code momentosFaltantes}, salvo que {@code
   * aceptarSinFotos} confirme que se valida igual. Si la OT no está cerrada, 400 con su estado: la
   * advertencia no aplica.
   */
  public DetalleOt validar(Long idOrdenTrabajo, boolean aceptarSinFotos) {
    OrdenTrabajo ot = accesoOt.buscarParaModificar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    if (ot.getEstado() == EstadoOT.CERRADA_TECNICA && !aceptarSinFotos) {
      List<MomentoFoto> faltantes = momentosFaltantes(ot);
      if (!faltantes.isEmpty()) {
        throw new FotosFaltantesException(faltantes);
      }
    }
    ot.validar(ahora);
    return guardarYPublicar(ot, ahora);
  }

  /** 4a: Cerrada técnicamente → Asignada, a la misma cuadrilla, con el motivo (obligatorio). */
  public DetalleOt observar(Long idOrdenTrabajo, String motivo) {
    OrdenTrabajo ot = accesoOt.buscarParaModificar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.observar(motivo, ahora);
    return guardarYPublicar(ot, ahora);
  }

  /** Lo que le falta al último cierre (3a): la regla está en {@link OrdenTrabajo}. */
  @Transactional(readOnly = true)
  public List<MomentoFoto> momentosFaltantes(OrdenTrabajo ot) {
    return ot.momentosFaltantes(
        adjuntoRepository.findByIdOrdenTrabajoOrderByCreadoEnAscIdAsc(ot.getId()));
  }

  private DetalleOt guardarYPublicar(OrdenTrabajo ot, Instant ahora) {
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos(usuarioActual.id()).forEach(eventos::publishEvent);
    return armadorDetalleOt.armar(ot, ahora);
  }
}
