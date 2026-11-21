package com.ciemsa.sigma.ot.domain;

import java.util.Optional;

/**
 * Tipos de foto aceptados (#71). Se reconocen por los primeros bytes del archivo (su «firma»), no
 * por la extensión ni por el tipo que declara quien lo sube, que se pueden falsear.
 */
public enum TipoImagen {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp");

  private final String mime;
  private final String extension;

  TipoImagen(String mime, String extension) {
    this.mime = mime;
    this.extension = extension;
  }

  public String mime() {
    return mime;
  }

  public String extension() {
    return extension;
  }

  /** JPEG: FF D8 FF. PNG: 89 50 4E 47 0D 0A 1A 0A. WEBP: «RIFF», 4 bytes de tamaño y «WEBP». */
  public static Optional<TipoImagen> detectar(byte[] contenido) {
    if (empiezaCon(contenido, 0, 0xFF, 0xD8, 0xFF)) {
      return Optional.of(JPEG);
    }
    if (empiezaCon(contenido, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.of(PNG);
    }
    if (empiezaCon(contenido, 0, 'R', 'I', 'F', 'F')
        && empiezaCon(contenido, 8, 'W', 'E', 'B', 'P')) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static boolean empiezaCon(byte[] contenido, int desde, int... firma) {
    if (contenido.length < desde + firma.length) {
      return false;
    }
    for (int i = 0; i < firma.length; i++) {
      if ((contenido[desde + i] & 0xFF) != firma[i]) {
        return false;
      }
    }
    return true;
  }
}
