package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Desactivar un técnico cuya cuadrilla tiene OTs (#50, CU-13d 3a) contra PostgreSQL. La cuadrilla
 * Norte tiene una OT de cada estado; solo cuentan Asignada, En proceso y En pausa.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DesactivarTecnicoTest {

  private static final String USUARIOS = "/api/v1/usuarios";
  private static final String HASH = "$2a$10$hashDePrueba";

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private final List<Long> cuadrillas = new ArrayList<>();
  private Instant ahora;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla norte;
  private Cuadrilla sur;
  private Cuadrilla oeste;
  private UsuarioAutenticado jefatura;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    norte = guardarCuadrilla("Cuadrilla Desactivar Norte");
    sur = guardarCuadrilla("Cuadrilla Desactivar Sur");
    oeste = guardarCuadrilla("Cuadrilla Desactivar Oeste");
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.desactivar@ciemsa.com", Rol.JEFATURA, null));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-DESACT-1",
                    "Cruce de desactivar",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();

    // Cuentan: asignada, en proceso y en pausa de Norte.
    guardarOt(ot -> ot.asignar(norte.getId(), LocalDate.now(), ahora));
    guardarOt(
        ot -> {
          ot.asignar(norte.getId(), LocalDate.now(), ahora);
          ot.iniciar(ahora);
        });
    guardarOt(
        ot -> {
          ot.asignar(norte.getId(), LocalDate.now(), ahora);
          ot.pausar(MotivoPausa.MATERIALES, null, jefatura.getId(), ahora);
        });
    // No cuentan: anulada, pendiente sin cuadrilla y la de otra cuadrilla.
    guardarOt(
        ot -> {
          ot.asignar(norte.getId(), LocalDate.now(), ahora);
          ot.anular("Duplicada", jefatura.getId(), ahora);
        });
    guardarOt(ot -> {});
    guardarOt(ot -> ot.asignar(sur.getId(), LocalDate.now(), ahora));
  }

  @AfterEach
  void tearDown() {
    String ordenesDeLaInstalacion =
        " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)";
    jdbc.update(
        "DELETE FROM pausa_ot WHERE id_orden_trabajo IN" + ordenesDeLaInstalacion, idInstalacion);
    jdbc.update(
        "DELETE FROM tramo_cuadrilla_ot WHERE id_orden_trabajo IN" + ordenesDeLaInstalacion,
        idInstalacion);
    jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", idInstalacion);
    usuarios.forEach(usuarioRepository::deleteById);
    cuadrillas.forEach(cuadrillaRepository::deleteById);
  }

  @Test
  void tecnicoConOtsEnSuCuadrilla_loDesactivaYAvisaCuantasTiene() throws Exception {
    Usuario tecnico =
        guardarUsuario("Técnico Norte", "norte.desactivar@ciemsa.com", Rol.TECNICO, norte);

    desactivar(tecnico)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cuadrilla").value("Cuadrilla Desactivar Norte"))
        .andExpect(jsonPath("$.otsAbiertas").value(3));
    assertThat(usuarioRepository.findById(tecnico.getId()).orElseThrow().isActivo()).isFalse();
  }

  @Test
  void tecnicoDeCuadrillaSinOts_avisaCero() throws Exception {
    Usuario tecnico =
        guardarUsuario("Técnico Oeste", "oeste.desactivar@ciemsa.com", Rol.TECNICO, oeste);

    desactivar(tecnico)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cuadrilla").value("Cuadrilla Desactivar Oeste"))
        .andExpect(jsonPath("$.otsAbiertas").value(0));
  }

  @Test
  void operador_sinAviso() throws Exception {
    Usuario operador =
        guardarUsuario("Operador", "operador.desactivar@ciemsa.com", Rol.OPERADOR, null);

    desactivar(operador)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cuadrilla").value(nullValue()))
        .andExpect(jsonPath("$.otsAbiertas").value(0));
  }

  // --- Ayudas ---

  private ResultActions desactivar(Usuario usuario) throws Exception {
    return mvc.perform(
        post(USUARIOS + "/" + usuario.getId() + "/desactivar").with(user(jefatura)).with(csrf()));
  }

  /** Una OT correctiva sin SLA en la instalación del test, con el ajuste aplicado. */
  private void guardarOt(Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente =
        incidenteRepository.save(
            Incidente.registrar(
                idInstalacion,
                OrigenIncidente.MONITOREO,
                idCategoria,
                Prioridad.MEDIA,
                "Falla de prueba",
                jefatura.getId()));
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(ahora, null);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    ordenTrabajoRepository.save(ot);
  }

  private Cuadrilla guardarCuadrilla(String nombre) {
    Cuadrilla cuadrilla = cuadrillaRepository.save(Cuadrilla.crear(nombre, null));
    cuadrillas.add(cuadrilla.getId());
    return cuadrilla;
  }

  private Usuario guardarUsuario(String nombre, String correo, Rol rol, Cuadrilla cuadrilla) {
    Usuario usuario = usuarioRepository.save(Usuario.crear(nombre, correo, HASH, rol, cuadrilla));
    usuarios.add(usuario.getId());
    return usuario;
  }

  /** El mismo objeto que arma el login, para simular una sesión iniciada. */
  private static UsuarioAutenticado enSesion(Usuario usuario) {
    return new UsuarioAutenticado(
        usuario.getId(),
        usuario.getCorreo(),
        usuario.getNombre(),
        usuario.getRol(),
        usuario.getCuadrilla() == null ? null : usuario.getCuadrilla().getId(),
        true,
        null,
        null);
  }
}
