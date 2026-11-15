package com.ciemsa.sigma.auditoria.dto;

import com.ciemsa.sigma.auditoria.AccionAuditada;
import com.ciemsa.sigma.auditoria.EntidadAuditada;
import com.ciemsa.sigma.auditoria.RegistroAuditoria;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;

/** Una fila de la bitácora; los valores van como el JSON guardado, no como texto. */
public record RegistroAuditoriaResponse(
    Long id,
    EntidadAuditada entidad,
    Long idEntidad,
    AccionAuditada accion,
    Long idUsuario,
    String nombreUsuario,
    Instant fechaHora,
    @JsonRawValue String valorAnterior,
    @JsonRawValue String valorNuevo) {

  public static RegistroAuditoriaResponse de(RegistroAuditoria r) {
    return new RegistroAuditoriaResponse(
        r.getId(),
        r.getEntidad(),
        r.getIdEntidad(),
        r.getAccion(),
        r.getIdUsuario(),
        r.getNombreUsuario(),
        r.getFechaHora(),
        r.getValorAnterior(),
        r.getValorNuevo());
  }
}
