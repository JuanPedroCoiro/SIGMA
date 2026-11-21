package com.ciemsa.sigma.ot.domain;

import java.time.Instant;

/**
 * Se registró un incidente (CU-1), con los datos que quedan en la bitácora (#80) y su estado
 * inicial (#86): ABIERTO, o EN_ESPERA_TERCERO si la falla es de un tercero (4b). Se publica antes
 * de generar la OT, así que nunca llega con CON_OT. {@code tercero} y {@code causa} son null si la
 * falla no es de un tercero.
 */
public record IncidenteRegistrado(
    Long idIncidente,
    Long idInstalacion,
    Long idCategoriaFalla,
    OrigenIncidente origen,
    Prioridad prioridad,
    EstadoIncidente estado,
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
        incidente.getEstado(),
        incidente.getTercero(),
        incidente.getCausa(),
        idUsuario,
        ocurridoEn);
  }
}
