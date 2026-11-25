package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.TipoImagen;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ejecución de la OT por el técnico (CU-3, HU-07): iniciar la intervención y el cierre técnico.
 * Solo sobre las OTs de su cuadrilla actual ({@link AccesoOt}); desde qué estados se puede lo
 * decide EstadoOT. Los cambios de estado quedan en la bitácora por {@code EstadoOrdenCambiado}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EjecucionOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final AccesoOt accesoOt;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;
  private final FotosCierreService fotosCierreService;

  /** Paso 2: Asignada → En proceso, con la hora de inicio. */
  public DetalleOt iniciar(Long idOrdenTrabajo) {
    OrdenTrabajo ot = accesoOt.buscarParaModificar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    ot.iniciar(ahora);
    return guardarYPublicar(ot, ahora);
  }

  /**
   * Pasos 6 a 10: En proceso → Cerrada técnicamente con sus fotos (HU-08), en una sola transacción.
   * Idempotente (R8, 7b y 8a): si la OT ya se cerró con {@code idOperacion}, devuelve el resultado
   * anterior sin cerrar de nuevo ni guardar las fotos otra vez. La OT se bloquea para que dos
   * reintentos simultáneos no compitan. 8b: si ya no es de la cuadrilla (reasignada) es 403; si ya
   * no está En proceso (anulada, cerrada con otro envío), 400 con el estado actual.
   */
  public DetalleOt cerrar(
      Long idOrdenTrabajo, UUID idOperacion, CierreTecnico cierre, List<FotoCierre> fotos) {
    Objects.requireNonNull(idOperacion, "Falta el id de operación.");
    OrdenTrabajo ot = accesoOt.buscarParaModificar(idOrdenTrabajo);
    Instant ahora = Instant.now(clock);
    if (ot.cerradaCon(idOperacion)) {
      return armadorDetalleOt.armar(ot, ahora);
    }
    if (ordenTrabajoRepository.existsByIdOperacionCierre(idOperacion)) {
      throw new ConflictoException(
          "operacion-repetida", "Ese identificador de operación ya se usó para cerrar otra OT.");
    }
    List<TipoImagen> tipos = fotosCierreService.validar(fotos);
    ot.cerrarTecnicamente(cierre, idOperacion, ahora);
    fotosCierreService.guardar(ot.getId(), fotos, tipos);
    return guardarYPublicar(ot, ahora);
  }

  private DetalleOt guardarYPublicar(OrdenTrabajo ot, Instant ahora) {
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos(usuarioActual.id()).forEach(eventos::publishEvent);
    return armadorDetalleOt.armar(ot, ahora);
  }
}
