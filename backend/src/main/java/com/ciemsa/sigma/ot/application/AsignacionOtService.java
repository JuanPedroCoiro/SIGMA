package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.administracion.CuadrillaService;
import com.ciemsa.sigma.administracion.dto.CuadrillaResumen;
import com.ciemsa.sigma.ot.domain.FechaAnteriorException;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.TransicionInvalidaException;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asignar o reasignar una o más OTs a una cuadrilla con fecha de ejecución (CU-2, HU-05). Todo o
 * nada: si una OT no se puede asignar, no se asigna ninguna. Desde qué estados se puede lo decide
 * EstadoOT.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AsignacionOtService {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final CuadrillaService cuadrillaService;
  private final Clock clock;
  private final ZoneId zonaHoraria;
  private final ApplicationEventPublisher eventos;
  private final ArmadorDetalleOt armadorDetalleOt;

  public List<DetalleOt> asignar(
      Collection<Long> idsOrdenTrabajo, Long idCuadrilla, LocalDate fechaEjecucion) {
    CuadrillaResumen cuadrilla = cuadrillaActiva(idCuadrilla);
    Instant ahora = Instant.now(clock);
    verificarFecha(fechaEjecucion, ahora);
    List<OrdenTrabajo> ordenes = buscarTodas(idsOrdenTrabajo);

    for (OrdenTrabajo ot : ordenes) {
      try {
        ot.asignar(cuadrilla.id(), fechaEjecucion, ahora);
      } catch (TransicionInvalidaException ex) {
        throw ex.deLaOt(ot.getNumero());
      }
    }

    ordenTrabajoRepository.saveAll(ordenes);
    ordenes.forEach(ot -> ot.extraerEventos().forEach(eventos::publishEvent));
    return armadorDetalleOt.armar(ordenes, ahora);
  }

  private CuadrillaResumen cuadrillaActiva(Long idCuadrilla) {
    return cuadrillaService
        .buscarActiva(Objects.requireNonNull(idCuadrilla))
        .orElseThrow(
            () ->
                new ReglaNegocioException(
                    "La cuadrilla " + idCuadrilla + " no existe o está inactiva."));
  }

  /** 2a: la fecha se compara con «hoy» en Montevideo, no en UTC. */
  private void verificarFecha(LocalDate fechaEjecucion, Instant ahora) {
    LocalDate hoy = LocalDate.ofInstant(ahora, zonaHoraria);
    if (Objects.requireNonNull(fechaEjecucion).isBefore(hoy)) {
      throw new FechaAnteriorException();
    }
  }

  /** Sin repetidos y en el orden pedido; 404 con todas las que no existen. */
  private List<OrdenTrabajo> buscarTodas(Collection<Long> idsOrdenTrabajo) {
    Set<Long> ids = new LinkedHashSet<>(idsOrdenTrabajo);
    Map<Long, OrdenTrabajo> encontradas =
        ordenTrabajoRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(OrdenTrabajo::getId, Function.identity()));
    List<Long> faltantes = ids.stream().filter(id -> !encontradas.containsKey(id)).toList();
    if (!faltantes.isEmpty()) {
      throw new NoEncontradoException("No existen las órdenes de trabajo " + faltantes + ".");
    }
    return ids.stream().map(encontradas::get).toList();
  }
}
