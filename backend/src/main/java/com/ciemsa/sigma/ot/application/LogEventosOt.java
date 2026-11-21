package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.EstadoOrdenCambiado;
import com.ciemsa.sigma.ot.domain.IncidenteCerradoSinOt;
import com.ciemsa.sigma.ot.domain.IncidenteRegistrado;
import com.ciemsa.sigma.ot.domain.OrdenAnulada;
import com.ciemsa.sigma.ot.domain.OrdenAsignada;
import com.ciemsa.sigma.ot.domain.OrdenGenerada;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Logs técnicos de los eventos de negocio de incidentes y OTs (#95). No son la bitácora (HU-12):
 * sirven para diagnosticar y no se guardan en la base. Solo ids y estados, nunca textos libres
 * (descripción, motivo), que pueden traer datos personales.
 */
@Slf4j
@Component
class LogEventosOt {

  @TransactionalEventListener(fallbackExecution = true)
  public void on(IncidenteRegistrado e) {
    log.info(
        "Incidente {} registrado en la instalación {} con prioridad {}",
        e.idIncidente(),
        e.idInstalacion(),
        e.prioridad());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void on(IncidenteCerradoSinOt e) {
    log.info("Incidente {} cerrado sin OT", e.idIncidente());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void on(OrdenGenerada e) {
    log.info("OT {} generada para el incidente {}", e.idOrdenTrabajo(), e.idIncidente());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void on(OrdenAsignada e) {
    log.info(
        "OT {} asignada a la cuadrilla {} (antes: {}) para el {}",
        e.idOrdenTrabajo(),
        e.idCuadrillaNueva(),
        e.idCuadrillaAnterior(),
        e.fechaEjecucion());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void on(EstadoOrdenCambiado e) {
    log.info("OT {} pasó de {} a {}", e.idOrdenTrabajo(), e.estadoAnterior(), e.estadoNuevo());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void on(OrdenAnulada e) {
    log.info("OT {} anulada", e.idOrdenTrabajo());
  }
}
