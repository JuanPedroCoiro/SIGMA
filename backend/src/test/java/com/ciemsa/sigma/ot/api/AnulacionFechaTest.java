package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * La fecha de anulación de una OT es su {@code modificado_en} (HU-10, historial): ANULADA es un
 * estado final, así que nada vuelve a modificar la fila. Con un reloj que se mueve a mano se
 * verifica que la anulación deja exactamente la hora del Clock, la misma que usa la auditoría JPA.
 * Si alguna vez una anulada se modifica, hace falta {@code anulada_en} con su migración.
 */
@Import({TestcontainersConfiguration.class, AnulacionFechaTest.ConReloj.class})
@SpringBootTest
class AnulacionFechaTest {

  private static final Instant AL_GENERAR = Instant.parse("2026-11-20T12:00:00.000001Z");
  private static final Instant AL_ANULAR = Instant.parse("2026-11-20T15:30:45.123456Z");

  @TestConfiguration
  static class ConReloj {
    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
      return new RelojDePrueba();
    }
  }

  /** Un Clock que marca la hora que le fija el test. */
  static class RelojDePrueba extends Clock {
    private volatile Instant ahora = AL_GENERAR;

    void fijar(Instant instante) {
      ahora = instante;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
      return this;
    }

    @Override
    public Instant instant() {
      return ahora;
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private RelojDePrueba reloj;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private Usuario usuario;
  private Long idInstalacion;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    reloj.fijar(AL_GENERAR);
    usuario =
        usuarioRepository.save(
            Usuario.crear(
                "Jefatura",
                "jefatura.fecha-anulacion@ciemsa.com",
                "$2a$10$hash",
                Rol.JEFATURA,
                null));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-ANULADA-1",
                    "Cruce de la fecha de anulación",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
  }

  @AfterEach
  void tearDown() {
    jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", idInstalacion);
    usuarioRepository.deleteById(usuario.getId());
  }

  @Test
  void anular_dejaEnModificadoEnLaHoraDelClock() throws Exception {
    Long idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    Incidente incidente =
        incidenteRepository.save(
            Incidente.registrar(
                idInstalacion,
                OrigenIncidente.MONITOREO,
                idCategoria,
                Prioridad.MEDIA,
                "Falla de prueba",
                usuario.getId()));
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(AL_GENERAR, null);
    incidenteRepository.save(incidente);
    ot = ordenTrabajoRepository.save(ot);
    assertThat(modificadoEn(ot)).isEqualTo(AL_GENERAR);

    reloj.fijar(AL_ANULAR);
    mvc.perform(
            post("/api/v1/ordenes/" + ot.getId() + "/anular")
                .with(user(enSesion(usuario)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Duplicada\"}"))
        .andExpect(status().isOk());

    assertThat(modificadoEn(ot)).isEqualTo(AL_ANULAR);
  }

  private Instant modificadoEn(OrdenTrabajo ot) {
    return jdbc.queryForObject(
        "SELECT modificado_en FROM orden_trabajo WHERE id_orden_trabajo = ?",
        Instant.class,
        ot.getId());
  }

  private static UsuarioAutenticado enSesion(Usuario usuario) {
    return new UsuarioAutenticado(
        usuario.getId(),
        usuario.getCorreo(),
        usuario.getNombre(),
        usuario.getRol(),
        null,
        true,
        null,
        null);
  }
}
