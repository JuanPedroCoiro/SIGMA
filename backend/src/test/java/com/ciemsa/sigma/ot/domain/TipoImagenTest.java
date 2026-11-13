package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TipoImagenTest {

  @Test
  void jpeg() {
    assertThat(TipoImagen.detectar(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00))).contains(TipoImagen.JPEG);
  }

  @Test
  void png() {
    assertThat(TipoImagen.detectar(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        .contains(TipoImagen.PNG);
  }

  @Test
  void webp() {
    assertThat(TipoImagen.detectar(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P')))
        .contains(TipoImagen.WEBP);
  }

  @Test
  void textoConExtensionDeImagen_noEsImagen() {
    // Lo que importa es el contenido: un .jpg que en realidad es texto no pasa.
    assertThat(TipoImagen.detectar("no soy una foto".getBytes(StandardCharsets.UTF_8))).isEmpty();
  }

  @Test
  void otroRiffQueNoEsWebp_noEsImagen() {
    // Un WAV también empieza con RIFF: hay que mirar los bytes 8 a 11.
    assertThat(TipoImagen.detectar(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E')))
        .isEmpty();
  }

  @Test
  void muyCorto_noEsImagen() {
    assertThat(TipoImagen.detectar(bytes(0xFF, 0xD8))).isEmpty();
    assertThat(TipoImagen.detectar(new byte[0])).isEmpty();
  }

  private static byte[] bytes(int... valores) {
    byte[] resultado = new byte[valores.length];
    for (int i = 0; i < valores.length; i++) {
      resultado[i] = (byte) valores[i];
    }
    return resultado;
  }
}
