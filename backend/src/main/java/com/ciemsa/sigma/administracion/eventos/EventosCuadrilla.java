package com.ciemsa.sigma.administracion.eventos;

import java.time.Instant;

/** Eventos de la gestión de cuadrillas. {@code idUsuario} es quien hizo la acción. */
public final class EventosCuadrilla {

  private EventosCuadrilla() {}

  public record CuadrillaCreada(
      Long idCuadrilla, DatosCuadrilla datos, Long idUsuario, Instant ocurridoEn) {}

  public record CuadrillaModificada(
      Long idCuadrilla,
      DatosCuadrilla anterior,
      DatosCuadrilla nuevo,
      Long idUsuario,
      Instant ocurridoEn) {}

  public record CuadrillaDesactivada(Long idCuadrilla, Long idUsuario, Instant ocurridoEn) {}

  public record CuadrillaActivada(Long idCuadrilla, Long idUsuario, Instant ocurridoEn) {}
}
