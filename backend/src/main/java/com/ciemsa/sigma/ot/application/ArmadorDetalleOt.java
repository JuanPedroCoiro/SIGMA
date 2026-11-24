package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.TramoCuadrillaOT;
import com.ciemsa.sigma.ot.infrastructure.DetalleOtLectura;
import com.ciemsa.sigma.ot.infrastructure.FilaDetalleOt;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Arma el {@link DetalleOt} que devuelven el detalle y todas las acciones sobre la OT (pausar,
 * reanudar, anular, asignar, tomar, liberar). Corre en la transacción del servicio que lo llama:
 * Hibernate hace flush de la OT modificada antes de la consulta, así que lee la cuadrilla nueva.
 */
@Component
@RequiredArgsConstructor
class ArmadorDetalleOt {

  private final DetalleOtLectura lectura;

  DetalleOt armar(OrdenTrabajo ot, Instant ahora) {
    return armar(List.of(ot), ahora).getFirst();
  }

  /** En el mismo orden que {@code ordenes}, con tres consultas en total. */
  List<DetalleOt> armar(List<OrdenTrabajo> ordenes, Instant ahora) {
    Map<Long, FilaDetalleOt> filas =
        lectura.filas(ordenes.stream().map(OrdenTrabajo::getId).toList());
    Set<Long> idsUsuarios =
        ordenes.stream()
            .flatMap(ot -> ot.getPausas().stream())
            .flatMap(p -> Stream.of(p.getIdUsuarioPausa(), p.getIdUsuarioReanudacion()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> nombres = lectura.nombresDeUsuarios(idsUsuarios);
    Set<Long> idsCuadrillas =
        ordenes.stream()
            .flatMap(ot -> ot.getTramos().stream())
            .map(TramoCuadrillaOT::getIdCuadrilla)
            .collect(Collectors.toSet());
    Map<Long, String> cuadrillas = lectura.nombresDeCuadrillas(idsCuadrillas);
    return ordenes.stream()
        .map(
            ot ->
                detalle(
                    ot, Objects.requireNonNull(filas.get(ot.getId())), nombres, cuadrillas, ahora))
        .toList();
  }

  private static DetalleOt detalle(
      OrdenTrabajo ot,
      FilaDetalleOt fila,
      Map<Long, String> nombres,
      Map<Long, String> cuadrillas,
      Instant ahora) {
    return new DetalleOt(
        ot,
        ot.estaVencida(ahora),
        ot.tiempos(ahora),
        List.copyOf(ot.getPausas()),
        new DetalleOt.Instalacion(
            fila.idInstalacion(), fila.codigoInstalacion(), fila.nombreInstalacion()),
        fila.idCuadrilla() == null
            ? null
            : new DetalleOt.Cuadrilla(fila.idCuadrilla(), fila.nombreCuadrilla()),
        fila.idIncidente() == null
            ? null
            : new DetalleOt.Incidente(
                fila.idIncidente(),
                fila.numeroIncidente(),
                fila.origen(),
                fila.categoria(),
                fila.prioridad(),
                fila.descripcion(),
                fila.tercero(),
                fila.causa()),
        nombres,
        ot.getTramos().stream()
            .map(
                t ->
                    new DetalleOt.Tramo(
                        t.getIdCuadrilla(),
                        cuadrillas.get(t.getIdCuadrilla()),
                        t.getDesde(),
                        t.getHasta()))
            .toList());
  }
}
