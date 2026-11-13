package com.ciemsa.sigma.shared.archivos.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unitario, sin Spring: un directorio temporal por test. */
class AlmacenArchivosEnDiscoTest {

  private static final byte[] CONTENIDO = {1, 2, 3, 4};

  @TempDir Path directorio;
  private AlmacenArchivosEnDisco almacen;

  @BeforeEach
  void setUp() {
    almacen = new AlmacenArchivosEnDisco(directorio);
  }

  @Test
  void guardarYLeer_devuelveElMismoContenidoConNombreAlAzar() {
    String ubicacion = almacen.guardar(CONTENIDO, "jpg");

    assertThat(ubicacion).endsWith(".jpg").matches("[0-9a-f]{2}/[0-9a-f-]{36}\\.jpg");
    assertThat(Files.exists(directorio.resolve(ubicacion))).isTrue();
    assertThat(almacen.leer(ubicacion)).isEqualTo(CONTENIDO);
  }

  @Test
  void dosVeces_dosArchivosDistintos() {
    assertThat(almacen.guardar(CONTENIDO, "png")).isNotEqualTo(almacen.guardar(CONTENIDO, "png"));
  }

  @Test
  void borrar_yDespuesLeer_noEncontrado() {
    String ubicacion = almacen.guardar(CONTENIDO, "webp");

    almacen.borrar(ubicacion);

    assertThatThrownBy(() -> almacen.leer(ubicacion)).isInstanceOf(NoEncontradoException.class);
  }

  @Test
  void borrar_inexistente_noFalla() {
    almacen.borrar("ab/no-existe.jpg");
  }

  @Test
  void ubicacionFueraDelDirectorio_rechazada() {
    assertThatThrownBy(() -> almacen.leer("../otro/archivo.jpg"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void extensionInvalida_rechazada() {
    assertThatThrownBy(() -> almacen.guardar(CONTENIDO, "../jpg"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
