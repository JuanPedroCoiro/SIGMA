package com.ciemsa.sigma.datosdemo;

import static org.assertj.core.api.Assertions.assertThat;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.shared.configuracion.RelojDesplazable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Escenario de la demo (#81) contra PostgreSQL, como arranca el perfil dev: con los datos de
 * desarrollo y el generador activo. Tiene su propio contexto (y su propia base), así los usuarios y
 * las OTs de la demo no se mezclan con los demás tests de integración.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "spring.flyway.locations=classpath:db/migration,classpath:db/datos-prueba,classpath:db/datos-dev",
      "sigma.datos-demo.habilitado=true"
    })
@ActiveProfiles("dev")
class DatosDemoTest {

  private static final String ABIERTAS = "('PENDIENTE', 'ASIGNADA', 'EN_PROCESO', 'EN_PAUSA')";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private GeneradorDatosDemo generador;
  @Autowired private Clock clock;

  @Test
  void alArrancarGeneraElEscenarioConTodosLosEstados() {
    assertThat(clock).isInstanceOf(RelojDesplazable.class);
    assertThat(contar("select count(*) from incidente")).isEqualTo(28);
    assertThat(porEstado("orden_trabajo"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("PENDIENTE", 12L, "ASIGNADA", 8L, "EN_PAUSA", 2L, "ANULADA", 2L));
    assertThat(porEstado("incidente"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("CON_OT", 24L, "EN_ESPERA_TERCERO", 3L, "CERRADO_SIN_OT", 1L));
  }

  @Test
  void hayVencidasPorVencerYSinSla() {
    // Las vencidas son ALTA registradas hace más de 8 h; una ya está asignada
    assertThat(
            contar(
                "select count(*) from orden_trabajo where estado in "
                    + ABIERTAS
                    + " and fecha_limite < now()"))
        .isEqualTo(3);
    assertThat(
            contar(
                "select count(*) from orden_trabajo where estado = 'ASIGNADA'"
                    + " and fecha_limite < now()"))
        .isEqualTo(1);
    assertThat(
            contar(
                "select count(*) from orden_trabajo where estado in "
                    + ABIERTAS
                    + " and fecha_limite between now() and now() + interval '24 hours'"))
        .isEqualTo(3);
    assertThat(
            contar(
                "select count(*) from orden_trabajo o join instalacion i using (id_instalacion)"
                    + " where o.fecha_limite is null and i.codigo = 'DEV-007'"))
        .isEqualTo(1);
  }

  @Test
  void lasAccionesQuedanConLaHoraYElUsuarioQueCorresponden() {
    // El reloj corrido llegó a la auditoría de las entidades: el incidente más viejo es de hace 48
    // h
    Instant masViejo =
        jdbc.queryForObject("select min(creado_en) from incidente", OffsetDateTime.class)
            .toInstant();
    assertThat(Duration.between(masViejo, Instant.now()))
        .isBetween(Duration.ofHours(47), Duration.ofHours(49));
    assertThat(contar("select count(*) from incidente where creado_en > now()")).isZero();

    // Registraron los dos operadores
    assertThat(contar("select count(distinct id_usuario) from incidente")).isEqualTo(2);

    // La reasignada tiene dos tramos y el primero quedó cerrado
    assertThat(
            contar(
                "select count(*) from (select id_orden_trabajo from tramo_cuadrilla_ot"
                    + " group by id_orden_trabajo having count(*) = 2) t"))
        .isEqualTo(1);

    // La reanudada tras un tercero: el SLA arrancó de nuevo al reanudar, hace 10 h
    Instant inicioSla =
        jdbc.queryForObject(
                "select o.inicio_sla from orden_trabajo o join pausa_ot p using (id_orden_trabajo)"
                    + " where p.fin is not null",
                OffsetDateTime.class)
            .toInstant();
    assertThat(Duration.between(inicioSla, Instant.now()))
        .isBetween(Duration.ofHours(10), Duration.ofHours(11));

    assertThat(
            contar(
                "select count(*) from orden_trabajo where estado = 'ANULADA'"
                    + " and motivo_anulacion is not null"))
        .isEqualTo(2);
    assertThat(
            contar(
                "select count(*) from pausa_ot where fin is null and motivo in ('TERCERO', 'MATERIALES')"))
        .isEqualTo(2);
  }

  @Test
  void esIdempotente() {
    long incidentes = contar("select count(*) from incidente");
    long ordenes = contar("select count(*) from orden_trabajo");

    assertThat(generador.generarSiHaceFalta()).isFalse();

    assertThat(contar("select count(*) from incidente")).isEqualTo(incidentes);
    assertThat(contar("select count(*) from orden_trabajo")).isEqualTo(ordenes);
  }

  @Test
  void elCatalogoTieneLasInstalacionesDeLasPruebas() {
    assertThat(contar("select count(*) from instalacion where codigo like 'DEV-%'")).isEqualTo(27);
    assertThat(
            jdbc.queryForObject(
                "select activa from instalacion where codigo = 'DEV-006'", Boolean.class))
        .isFalse();
    assertThat(
            contar(
                "select count(*) from regla_sla r join organismo o using (id_organismo)"
                    + " where o.nombre = 'Organismo Dev sin SLA'"))
        .isZero();
  }

  private long contar(String sql) {
    return jdbc.queryForObject(sql, Long.class);
  }

  private Map<String, Long> porEstado(String tabla) {
    return jdbc.query(
        "select estado, count(*) from " + tabla + " group by estado",
        rs -> {
          Map<String, Long> conteos = new HashMap<>();
          while (rs.next()) {
            conteos.put(rs.getString(1), rs.getLong(2));
          }
          return conteos;
        });
  }
}
