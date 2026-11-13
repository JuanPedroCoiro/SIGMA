package com.ciemsa.sigma.pendientes;

/**
 * Contadores de la página de inicio (#72). Cada rol recibe solo los suyos; los demás van en null y
 * el frontend no muestra esa tarjeta.
 *
 * <ul>
 *   <li>Jefatura: pendientesDeAsignar, vencidas, porVencer, enPausa, incidentesEnEspera.
 *   <li>Operador: vencidas, incidentesEnEspera, incidentesHoy.
 *   <li>Técnico: otsDeHoy (de su cuadrilla).
 * </ul>
 */
public record ResumenInicio(
    Long pendientesDeAsignar,
    Long vencidas,
    Long porVencer,
    Long enPausa,
    Long incidentesEnEspera,
    Long incidentesHoy,
    Long otsDeHoy) {

  public static ResumenInicio sinTarjetas() {
    return new ResumenInicio(null, null, null, null, null, null, null);
  }
}
