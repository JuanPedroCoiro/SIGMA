package com.ciemsa.sigma.administracion.eventos;

import java.time.Instant;

/**
 * Eventos de la gestión de usuarios (CU-13, HU-01, HU-20). En todos, {@code idCuenta} es la cuenta
 * sobre la que se actúa e {@code idUsuario} es quien hizo la acción (la Jefatura, o el técnico al
 * cambiarse de cuadrilla).
 */
public final class EventosUsuario {

  private EventosUsuario() {}

  /** CU-13a. */
  public record UsuarioCreado(
      Long idCuenta, DatosUsuario datos, Long idUsuario, Instant ocurridoEn) {}

  /** CU-13b: datos personales, rol o cuadrilla. */
  public record UsuarioModificado(
      Long idCuenta,
      DatosUsuario anterior,
      DatosUsuario nuevo,
      Long idUsuario,
      Instant ocurridoEn) {}

  /** CU-13b: solo la acción; la contraseña nunca viaja en el evento. */
  public record ContrasenaRestablecida(Long idCuenta, Long idUsuario, Instant ocurridoEn) {}

  /** CU-13d. */
  public record UsuarioDesactivado(Long idCuenta, Long idUsuario, Instant ocurridoEn) {}

  public record UsuarioActivado(Long idCuenta, Long idUsuario, Instant ocurridoEn) {}

  /**
   * HU-20: el técnico se cambió de cuadrilla él mismo, así que {@code idUsuario} es la misma
   * cuenta. El cambio que hace la Jefatura en la edición es un {@link UsuarioModificado}.
   */
  public record CuadrillaCambiada(
      Long idCuenta,
      Long idCuadrillaAnterior,
      Long idCuadrillaNueva,
      Long idUsuario,
      Instant ocurridoEn) {}
}
