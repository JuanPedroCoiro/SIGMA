package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.PausaOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TiemposOT;
import java.util.List;
import java.util.Map;

/**
 * Una OT con lo que se calcula al consultarla y lo que se lee de otras tablas, armado dentro de la
 * transacción (las pausas son lazy) por {@link ArmadorDetalleOt}. {@code tiempos} es null si la OT
 * ya no está abierta; {@code cuadrilla}, si no está asignada; {@code incidente}, si es preventiva.
 * {@code nombresUsuarios} tiene los nombres de quienes pausaron y reanudaron, por id.
 */
public record DetalleOt(
    OrdenTrabajo ordenTrabajo,
    boolean vencida,
    TiemposOT tiempos,
    List<PausaOT> pausas,
    Instalacion instalacion,
    Cuadrilla cuadrilla,
    Incidente incidente,
    Map<Long, String> nombresUsuarios) {

  public record Instalacion(Long id, String codigo, String nombre) {}

  public record Cuadrilla(Long id, String nombre) {}

  /** {@code categoria} es el nombre de la categoría de falla. */
  public record Incidente(
      Long id,
      String numero,
      OrigenIncidente origen,
      String categoria,
      Prioridad prioridad,
      String descripcion,
      String tercero,
      String causa) {}
}
