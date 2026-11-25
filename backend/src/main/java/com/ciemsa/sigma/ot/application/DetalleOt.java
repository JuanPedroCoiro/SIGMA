package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.MomentoFoto;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.PausaOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TiemposOT;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Una OT con lo que se calcula al consultarla y lo que se lee de otras tablas, armado dentro de la
 * transacción (las pausas y los tramos son lazy) por {@link ArmadorDetalleOt}. {@code tiempos} es
 * null si la OT ya no está abierta; {@code cuadrilla}, si no está asignada; {@code incidente}, si
 * es preventiva. {@code nombresUsuarios} tiene los nombres de quienes pausaron y reanudaron, por
 * id. {@code tramos}: qué cuadrilla tuvo la OT y cuándo (HU-20), del más viejo al más nuevo. {@code
 * fotosFaltantes}: si está cerrada técnicamente, los momentos obligatorios sin foto en el último
 * cierre (HU-09, CU-5 3a); vacía en cualquier otro estado.
 */
public record DetalleOt(
    OrdenTrabajo ordenTrabajo,
    boolean vencida,
    TiemposOT tiempos,
    List<PausaOT> pausas,
    Instalacion instalacion,
    Cuadrilla cuadrilla,
    Incidente incidente,
    Map<Long, String> nombresUsuarios,
    List<Tramo> tramos,
    List<MomentoFoto> fotosFaltantes) {

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

  /** {@code hasta} es null mientras la cuadrilla tiene la OT a cargo. */
  public record Tramo(Long idCuadrilla, String nombreCuadrilla, Instant desde, Instant hasta) {}
}
