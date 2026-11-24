package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.FotoInvalidaException;
import com.ciemsa.sigma.ot.domain.TipoImagen;

/** Una foto recién subida, todavía sin validar. Sin MultipartFile: la capa web queda en api. */
public record FotoNueva(String nombreOriginal, byte[] contenido) {

  public static final long TAMANIO_MAXIMO = 10L * 1024 * 1024;

  /**
   * JPG, PNG o WEBP de hasta 10 MB, reconocida por el contenido (#71). Devuelve su tipo; si no,
   * FotoInvalidaException.
   */
  public TipoImagen validar() {
    String nombre = nombreOriginal == null ? "La foto" : "«" + nombreOriginal + "»";
    if (contenido.length == 0) {
      throw new FotoInvalidaException(nombre + " está vacía.");
    }
    if (contenido.length > TAMANIO_MAXIMO) {
      throw new FotoInvalidaException(nombre + " supera los 10 MB.");
    }
    return TipoImagen.detectar(contenido)
        .orElseThrow(
            () -> new FotoInvalidaException(nombre + " no es una imagen JPG, PNG ni WEBP."));
  }
}
