package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.activos.dto.InstalacionFicha;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.DetalleOtLectura;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha de la instalación (HU-10, CU-7) para todos los roles. Cruza activos y ot, así que vive en
 * ot: activos no depende de este módulo. El historial (CU-8) va aparte, paginado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FichaInstalacionService {

  /** Primero la que vence antes; las que no tienen SLA, al final; a igual fecha, la más vieja. */
  private static final Comparator<OrdenTrabajo> POR_VENCIMIENTO =
      Comparator.comparing(
              OrdenTrabajo::getFechaLimite, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(OrdenTrabajo::getCreadoEn)
          .thenComparing(OrdenTrabajo::getId);

  private final InstalacionService instalacionService;
  private final OrganismoRepository organismoRepository;
  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final DetalleOtLectura lectura;
  private final AccesoOt accesoOt;
  private final Clock clock;

  /** 404 si la instalación no existe. Las inactivas también se consultan. */
  public FichaInstalacion obtener(Long idInstalacion) {
    InstalacionFicha instalacion = instalacionService.obtenerFicha(idInstalacion);
    FichaInstalacion.Organismo organismo =
        organismoRepository
            .findById(instalacion.idOrganismo())
            .map(o -> new FichaInstalacion.Organismo(o.getId(), o.getNombre()))
            .orElseThrow(); // la clave foránea garantiza que existe

    List<OrdenTrabajo> ordenes =
        ordenTrabajoRepository
            .findByIdInstalacionAndEstadoIn(idInstalacion, EstadoOT.EN_CURSO)
            .stream()
            .sorted(POR_VENCIMIENTO)
            .toList();
    Set<Long> idsCuadrillas =
        ordenes.stream()
            .map(OrdenTrabajo::getIdCuadrilla)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> cuadrillas = lectura.nombresDeCuadrillas(idsCuadrillas);
    Predicate<OrdenTrabajo> puedeVer = accesoOt.puedeVer();
    Instant ahora = Instant.now(clock);

    return new FichaInstalacion(
        instalacion,
        organismo,
        ordenes.stream().map(ot -> enCurso(ot, cuadrillas, puedeVer, ahora)).toList());
  }

  private static FichaInstalacion.OrdenEnCurso enCurso(
      OrdenTrabajo ot,
      Map<Long, String> cuadrillas,
      Predicate<OrdenTrabajo> puedeVer,
      Instant ahora) {
    Incidente incidente = ot.getIncidente();
    return new FichaInstalacion.OrdenEnCurso(
        ot.getId(),
        ot.getNumero(),
        ot.getTipo(),
        ot.getEstado(),
        incidente == null ? null : incidente.getPrioridad(),
        ot.getFechaLimite(),
        ot.estaVencida(ahora),
        ot.getIdCuadrilla() == null
            ? null
            : new DetalleOt.Cuadrilla(ot.getIdCuadrilla(), cuadrillas.get(ot.getIdCuadrilla())),
        incidente == null ? null : incidente.getNumero(),
        ot.getCreadoEn(),
        puedeVer.test(ot));
  }
}
