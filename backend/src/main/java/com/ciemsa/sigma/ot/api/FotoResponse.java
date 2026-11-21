package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.Adjunto;
import java.time.Instant;

/** Una foto del incidente, sin el contenido: {@code url} devuelve la imagen. */
public record FotoResponse(
    Long id, String nombreOriginal, String tipo, long tamanioBytes, Instant subidaEn, String url) {

  static FotoResponse de(Adjunto adjunto) {
    return new FotoResponse(
        adjunto.getId(),
        adjunto.getNombreOriginal(),
        adjunto.getTipo().mime(),
        adjunto.getTamanioBytes(),
        adjunto.getCreadoEn(),
        "/api/v1/incidentes/" + adjunto.getIdIncidente() + "/fotos/" + adjunto.getId());
  }
}
