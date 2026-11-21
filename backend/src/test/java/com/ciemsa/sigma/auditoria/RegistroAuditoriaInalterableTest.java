package com.ciemsa.sigma.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * La bitácora es inalterable en la base, no solo en la aplicación (RF-21): el trigger de V9 rechaza
 * UPDATE, DELETE y TRUNCATE aunque se ejecuten por fuera de SIGMA.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegistroAuditoriaInalterableTest {

  @Autowired private JdbcTemplate jdbc;

  private Long id;

  @BeforeEach
  void setUp() {
    id =
        jdbc.queryForObject(
            """
            INSERT INTO registro_auditoria
                (entidad, id_entidad, accion, id_usuario, nombre_usuario, fecha_hora, valor_nuevo)
            VALUES ('CUADRILLA', -1, 'ALTA', -1, 'Prueba', now(), '{"nombre": "Original"}')
            RETURNING id_registro_auditoria
            """,
            Long.class);
  }

  @Test
  void update_esRechazado() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE registro_auditoria SET nombre_usuario = 'Otro'"
                        + " WHERE id_registro_auditoria = ?",
                    id))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("inalterable");
    assertThat(nombreGuardado()).isEqualTo("Prueba");
  }

  @Test
  void delete_esRechazado() {
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM registro_auditoria WHERE id_registro_auditoria = ?", id))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("inalterable");
    assertThat(nombreGuardado()).isEqualTo("Prueba");
  }

  @Test
  void truncate_esRechazado() {
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE registro_auditoria"))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("inalterable");
    assertThat(nombreGuardado()).isEqualTo("Prueba");
  }

  private String nombreGuardado() {
    return jdbc.queryForObject(
        "SELECT nombre_usuario FROM registro_auditoria WHERE id_registro_auditoria = ?",
        String.class,
        id);
  }
}
