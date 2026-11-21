package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
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
import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.CuadrillaRepository;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.EstadoOrdenCambiado;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Anular una OT (#44, CU-19) contra PostgreSQL. Las OTs son correctivas sin SLA en la instalación
 * TEST-ANULAR-1; cada test arma la suya en el estado que necesita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class AnulacionOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String HASH = "$2a$10$hashDePrueba";

  @Autowired private WebApplicationContext context;
  @Autowired private ApplicationEvents eventos;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private Instant ahora;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla cuadrilla;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnico;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    cuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Anular", null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.anular@ciemsa.com", Rol.JEFATURA, null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.anular@ciemsa.com", Rol.OPERADOR, null));
    tecnico =
        enSesion(guardarUsuario("Técnico", "tecnico.anular@ciemsa.com", Rol.TECNICO, cuadrilla));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-ANULAR-1",
                    "Cruce de anular",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
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
    cuadrillaRepository.deleteById(cuadrilla.getId());
  }

  // --- Desde dónde se puede anular ---

  @Test
  void pendiente_laAnulaGuardaElMotivoYPublicaElCambioDeEstado() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> {});

    anular(jefatura, ot, "Duplicada")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADA"));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getEstado()).isEqualTo(EstadoOT.ANULADA);
    assertThat(guardada.getMotivoAnulacion()).isEqualTo("Duplicada");
    assertThat(eventos.stream(EstadoOrdenCambiado.class))
        .anySatisfy(
            e -> {
              assertThat(e.idOrdenTrabajo()).isEqualTo(ot.getId());
              assertThat(e.estadoAnterior()).isEqualTo(EstadoOT.PENDIENTE);
              assertThat(e.estadoNuevo()).isEqualTo(EstadoOT.ANULADA);
            });
  }

  @Test
  void asignada_laAnula() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> o.asignar(cuadrilla.getId(), LocalDate.now(), ahora));

    anular(jefatura, ot, "Improcedente")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADA"));
  }

  @Test
  void enPausaDesdeAsignada_laAnulaYCierraLaPausa() throws Exception {
    OrdenTrabajo ot =
        guardarOt(
            o -> {
              o.asignar(cuadrilla.getId(), LocalDate.now(), ahora);
              o.pausar(MotivoPausa.TERCERO, "UTE", operador.getId(), ahora);
            });

    anular(jefatura, ot, "El tercero resolvió la falla")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADA"))
        .andExpect(jsonPath("$.pausaAbierta", nullValue()))
        .andExpect(jsonPath("$.pausas[0].fin", notNullValue()))
        .andExpect(jsonPath("$.pausas[0].nombreUsuarioPausa").value("Operador"))
        .andExpect(jsonPath("$.pausas[0].nombreUsuarioReanudacion").value("Jefatura"));
  }

  // --- Desde dónde no (5a) ---

  @Test
  void enProceso_400TransicionInvalidaYNoCambia() throws Exception {
    OrdenTrabajo ot =
        guardarOt(
            o -> {
              o.asignar(cuadrilla.getId(), LocalDate.now(), ahora);
              o.iniciar(ahora);
            });

    anular(jefatura, ot, "Duplicada")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"));
    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.EN_PROCESO);
  }

  @Test
  void enPausaDesdeEnProceso_400TransicionInvalida() throws Exception {
    OrdenTrabajo ot =
        guardarOt(
            o -> {
              o.asignar(cuadrilla.getId(), LocalDate.now(), ahora);
              o.iniciar(ahora);
              o.pausar(MotivoPausa.CLIMA, null, operador.getId(), ahora);
            });

    anular(jefatura, ot, "Duplicada")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"));
  }

  @Test
  void yaAnulada_400TransicionInvalida() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> o.anular("Duplicada", jefatura.getId(), ahora));

    anular(jefatura, ot, "Otra vez")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"));
  }

  // --- Datos y permisos ---

  @Test
  void sinMotivo_400ValidacionYNoCambia() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> {});

    anular(jefatura, ot, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("motivo")));
    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.PENDIENTE);
  }

  @Test
  void inexistente_404() throws Exception {
    mvc.perform(
            post(ORDENES + "/999999/anular")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Duplicada\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  @Test
  void operadorYTecnico_403() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> {});

    anular(operador, ot, "Duplicada").andExpect(status().isForbidden());
    anular(tecnico, ot, "Duplicada").andExpect(status().isForbidden());
    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.PENDIENTE);
  }

  // --- Ayudas ---

  private ResultActions anular(UsuarioAutenticado quien, OrdenTrabajo ot, String motivo)
      throws Exception {
    return mvc.perform(
        post(ORDENES + "/" + ot.getId() + "/anular")
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\": \"%s\"}".formatted(motivo)));
  }

  /** Una OT correctiva sin SLA en la instalación del test, con el ajuste aplicado. */
  private OrdenTrabajo guardarOt(Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente =
        incidenteRepository.save(
            Incidente.registrar(
                idInstalacion,
                OrigenIncidente.MONITOREO,
                idCategoria,
                Prioridad.MEDIA,
                "Falla de prueba",
                operador.getId()));
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(ahora, null);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    return ordenTrabajoRepository.save(ot);
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
