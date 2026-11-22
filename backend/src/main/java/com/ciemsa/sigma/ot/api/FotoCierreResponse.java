package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.Adjunto;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import java.time.Instant;

/** Una foto del cierre técnico (#90), sin el contenido: {@code url} devuelve la imagen. */
public record FotoCierreResponse(
    Long id,
    MomentoFoto momento,
    String nombreOriginal,
    String tipo,
    long tamanioBytes,
    Instant subidaEn,
    String url) {

  static FotoCierreResponse de(Adjunto adjunto) {
    return new FotoCierreResponse(
        adjunto.getId(),
        adjunto.getMomento(),
        adjunto.getNombreOriginal(),
        adjunto.getTipo().mime(),
        adjunto.getTamanioBytes(),
        adjunto.getCreadoEn(),
        "/api/v1/ordenes/" + adjunto.getIdOrdenTrabajo() + "/fotos/" + adjunto.getId());
  }
}
