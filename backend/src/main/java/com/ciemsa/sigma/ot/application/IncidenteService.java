package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.activos.dto.InstalacionResumen;
import com.ciemsa.sigma.ot.domain.CategoriaFalla;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.IncidenteCerradoSinOt;
import com.ciemsa.sigma.ot.domain.IncidenteRegistrado;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.ot.infrastructure.CategoriaFallaRepository;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.ReglaSlaRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registrar incidente (CU-1, HU-03): el incidente y su OT correctiva con el vencimiento del SLA en
 * una única transacción, o el incidente en espera de un tercero (4b) hasta resolverla.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IncidenteService {

  private final IncidenteRepository incidenteRepository;
  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final CategoriaFallaRepository categoriaFallaRepository;
  private final ReglaSlaRepository reglaSlaRepository;
  private final InstalacionService instalacionService;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;

  public IncidenteConOt registrar(RegistrarIncidenteCommand cmd) {
    InstalacionResumen instalacion = instalacionActiva(cmd.idInstalacion());
    CategoriaFalla categoria = categoriaActiva(cmd.idCategoriaFalla());
    categoria.validarDescripcion(cmd.descripcion());
    Prioridad prioridad =
        cmd.prioridad() != null ? cmd.prioridad() : categoria.getPrioridadSugerida();
    Instant ahora = ahora();
    // Paso 7 (RF-5, Etapa 2): verificar duplicados.

    Incidente incidente =
        Incidente.registrar(
            instalacion.id(),
            cmd.origen(),
            categoria.getId(),
            prioridad,
            cmd.descripcion().strip(),
            usuarioActual.id());
    if (cmd.esFallaDeTercero()) {
      incidente.ponerEnEsperaDeTercero(cmd.tercero(), cmd.causa());
    }
    incidenteRepository.save(incidente);
    eventos.publishEvent(new IncidenteRegistrado(incidente.getId(), usuarioActual.id(), ahora));

    if (incidente.estaEnEsperaDeTercero()) {
      return new IncidenteConOt(incidente, null);
    }
    return new IncidenteConOt(incidente, generarOt(incidente, instalacion, ahora));
  }

  /** Resuelve la espera de un tercero con una OT: el SLA se cuenta desde este momento (4b). */
  public IncidenteConOt generarOt(Long idIncidente) {
    Incidente incidente = buscar(idIncidente);
    if (!incidente.estaEnEsperaDeTercero()) {
      throw noEstaEnEspera(incidente);
    }
    InstalacionResumen instalacion = instalacionActiva(incidente.getIdInstalacion());
    return new IncidenteConOt(incidente, generarOt(incidente, instalacion, ahora()));
  }

  /** Resuelve la espera sin OT: el tercero volvió a comunicar (4b). */
  public IncidenteConOt cerrarSinOt(Long idIncidente) {
    Incidente incidente = buscar(idIncidente);
    incidente.cerrarSinOt();
    eventos.publishEvent(new IncidenteCerradoSinOt(incidente.getId(), usuarioActual.id(), ahora()));
    return new IncidenteConOt(incidente, null);
  }

  @Transactional(readOnly = true)
  public IncidenteConOt obtener(Long idIncidente) {
    Incidente incidente = buscar(idIncidente);
    return new IncidenteConOt(
        incidente, ordenTrabajoRepository.findByIncidenteId(idIncidente).orElse(null));
  }

  /** La regla se busca por el organismo de la instalación y la prioridad confirmada (RN-02). */
  private OrdenTrabajo generarOt(
      Incidente incidente, InstalacionResumen instalacion, Instant ahora) {
    ReglaSLA regla =
        reglaSlaRepository
            .findByIdOrganismoAndPrioridad(instalacion.idOrganismo(), incidente.getPrioridad())
            .orElse(null); // 8a: «SLA sin definir»
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(ahora, regla);
    ordenTrabajoRepository.save(ot);
    ot.extraerEventos().forEach(eventos::publishEvent);
    return ot;
  }

  /** Con la precisión de TIMESTAMPTZ (microsegundos): la respuesta coincide con lo guardado. */
  private Instant ahora() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  /** 3a: la instalación no existe o está inactiva. */
  private InstalacionResumen instalacionActiva(Long idInstalacion) {
    return instalacionService
        .buscarActiva(idInstalacion)
        .orElseThrow(
            () ->
                new ReglaNegocioException(
                    "La instalación " + idInstalacion + " no existe o está inactiva."));
  }

  private CategoriaFalla categoriaActiva(Long idCategoriaFalla) {
    return categoriaFallaRepository
        .findById(idCategoriaFalla)
        .filter(CategoriaFalla::isActiva)
        .orElseThrow(
            () ->
                new ReglaNegocioException(
                    "La categoría de falla " + idCategoriaFalla + " no existe o está inactiva."));
  }

  private Incidente buscar(Long idIncidente) {
    return incidenteRepository
        .findById(idIncidente)
        .orElseThrow(
            () -> new NoEncontradoException("No existe el incidente " + idIncidente + "."));
  }

  private static ReglaNegocioException noEstaEnEspera(Incidente incidente) {
    return new ReglaNegocioException(
        "El incidente " + incidente.getNumero() + " no está en espera de un tercero.");
  }
}
