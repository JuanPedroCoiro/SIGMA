package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * Se registró un incidente (CU-1), con los datos que quedan en la bitácora (#80). {@code tercero} y
 * {@code causa} son null si la falla no es de un tercero (4b).
 */
public record IncidenteRegistrado(
    Long idIncidente,
    Long idInstalacion,
    Long idCategoriaFalla,
    OrigenIncidente origen,
    Prioridad prioridad,
    String tercero,
    String causa,
    Long idUsuario,
    Instant ocurridoEn) {

  public static IncidenteRegistrado de(Incidente incidente, Long idUsuario, Instant ocurridoEn) {
    return new IncidenteRegistrado(
        incidente.getId(),
        incidente.getIdInstalacion(),
        incidente.getIdCategoriaFalla(),
        incidente.getOrigen(),
        incidente.getPrioridad(),
        incidente.getTercero(),
        incidente.getCausa(),
        idUsuario,
        ocurridoEn);
  }
}
