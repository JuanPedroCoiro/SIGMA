package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.ot.infrastructure.ReglaSlaRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Tomar y liberar una OT (#93, HU-20, CU-2 1a) contra PostgreSQL. La OT es una correctiva MEDIA de
 * MTOP que nace pendiente; Norte y Sur tienen un técnico cada una.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TomaOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final ZoneId MONTEVIDEO = ZoneId.of("America/Montevideo");

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private ReglaSlaRepository reglaSlaRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private final List<Long> cuadrillas = new ArrayList<>();
  private Long idInstalacion;

  private Cuadrilla norte;
  private Cuadrilla sur;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado tecnicoNorte;
  private UsuarioAutenticado tecnicoSur;
  private OrdenTrabajo ot;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    norte = guardarCuadrilla("Cuadrilla Toma Norte");
    sur = guardarCuadrilla("Cuadrilla Toma Sur");
    operador = enSesion(guardarUsuario("Operador", "operador.toma@ciemsa.com", Rol.OPERADOR, null));
    jefatura = enSesion(guardarUsuario("Jefatura", "jefatura.toma@ciemsa.com", Rol.JEFATURA, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico Norte", "norte.toma@ciemsa.com", Rol.TECNICO, norte));
    tecnicoSur = enSesion(guardarUsuario("Técnico Sur", "sur.toma@ciemsa.com", Rol.TECNICO, sur));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-TOMA-1",
                    "Cruce de toma",
                    "Ruta 8",
                    new BigDecimal("-34.790000"),
                    new BigDecimal("-56.060000"),
                    mtop))
            .getId();
    ot = guardarOtPendiente();
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

  // --- Tomar ---

  @Test
  void tomar_pendiente_quedaAsignadaASuCuadrillaConLaFechaDeHoy() throws Exception {
    tomar(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ASIGNADA"))
        .andExpect(jsonPath("$.cuadrilla.id").value(norte.getId()))
        .andExpect(jsonPath("$.fechaEjecucion").value(LocalDate.now(MONTEVIDEO).toString()));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
    assertThat(guardada.getIdCuadrilla()).isEqualTo(norte.getId());
    assertThat(tramosAbiertos()).isEqualTo(1);
  }

  @Test
  void tomar_quedaEnLaBitacoraComoAsignacionDelTecnico() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());

    assertThat(bitacora("ASIGNACION"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("id_usuario")).isEqualTo(tecnicoNorte.getId());
              assertThat(r.get("anterior")).isNull();
              assertThat(r.get("nuevo")).isEqualTo(norte.getId().toString());
            });
  }

  @Test
  void tomar_yaTomadaPorOtraCuadrilla_devuelve400YNoSeLaQuita() throws Exception {
    tomar(tecnicoSur).andExpect(status().isOk());

    tomar(tecnicoNorte)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("ASIGNADA"));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getIdCuadrilla()).isEqualTo(sur.getId());
    assertThat(tramosAbiertos()).isEqualTo(1);
  }

  @Test
  void tomar_operadorOJefatura_devuelve403() throws Exception {
    tomar(operador).andExpect(status().isForbidden());
    tomar(jefatura).andExpect(status().isForbidden());

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.PENDIENTE);
  }

  @Test
  void tomar_inexistente_devuelve404() throws Exception {
    mvc.perform(post(ORDENES + "/999999/tomar").with(user(tecnicoNorte)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  // --- Liberar ---

  @Test
  void liberar_enProceso_vuelveAPendienteSinCuadrillaYConservaElInicio() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());
    accion(tecnicoNorte, "iniciar").andExpect(status().isOk());
    Instant iniciada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getIniciadaEn();

    accion(tecnicoNorte, "liberar")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.cuadrilla").value(nullValue()))
        .andExpect(jsonPath("$.fechaEjecucion").value(nullValue()));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getIdCuadrilla()).isNull();
    assertThat(guardada.getIniciadaEn()).isEqualTo(iniciada);
    assertThat(tramosAbiertos()).isZero();
  }

  @Test
  void liberar_quedaEnLaBitacoraConLaCuadrillaQueLaTenia() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());

    accion(tecnicoNorte, "liberar").andExpect(status().isOk());

    assertThat(bitacora("LIBERACION"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("id_usuario")).isEqualTo(tecnicoNorte.getId());
              assertThat(r.get("anterior")).isEqualTo(norte.getId().toString());
              assertThat(r.get("nuevo")).isNull();
            });
  }

  @Test
  void liberarYTomarOtraCuadrilla_dosTramosYQuedaDeLaSegunda() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());
    accion(tecnicoNorte, "liberar").andExpect(status().isOk());

    tomar(tecnicoSur)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cuadrilla.id").value(sur.getId()));

    assertThat(tramos()).isEqualTo(2);
    assertThat(tramosAbiertos()).isEqualTo(1);
  }

  @Test
  void liberar_tecnicoDeOtraCuadrilla_devuelve403() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());

    accion(tecnicoSur, "liberar")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.ASIGNADA);
  }

  @Test
  void liberar_pendiente_devuelve403PorqueNoEsDeSuCuadrilla() throws Exception {
    accion(tecnicoNorte, "liberar").andExpect(status().isForbidden());
  }

  @Test
  void liberarYTomarOtraCuadrilla_elDetalleMuestraLosTramos() throws Exception {
    tomar(tecnicoNorte).andExpect(status().isOk());
    accion(tecnicoNorte, "liberar").andExpect(status().isOk());

    tomar(tecnicoSur)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tramos", hasSize(2)))
        .andExpect(jsonPath("$.tramos[0].idCuadrilla").value(norte.getId()))
        .andExpect(jsonPath("$.tramos[0].nombreCuadrilla").value("Cuadrilla Toma Norte"))
        .andExpect(jsonPath("$.tramos[0].hasta").isNotEmpty())
        .andExpect(jsonPath("$.tramos[1].idCuadrilla").value(sur.getId()))
        .andExpect(jsonPath("$.tramos[1].hasta").value(nullValue()));
  }

  // --- Auxiliares ---

  private ResultActions tomar(UsuarioAutenticado quien) throws Exception {
    return accion(quien, "tomar");
  }

  private ResultActions accion(UsuarioAutenticado quien, String accion) throws Exception {
    return mvc.perform(
        post(ORDENES + "/" + ot.getId() + "/" + accion).with(user(quien)).with(csrf()));
  }

  private EstadoOT estadoEnLaBase() {
    return ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado();
  }

  private long tramos() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM tramo_cuadrilla_ot WHERE id_orden_trabajo = ?",
        Long.class,
        ot.getId());
  }

  private long tramosAbiertos() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM tramo_cuadrilla_ot WHERE id_orden_trabajo = ? AND hasta IS NULL",
        Long.class,
        ot.getId());
  }

  /** Registros de la OT con esa acción: quién y la cuadrilla anterior y nueva (como texto). */
  private List<Map<String, Object>> bitacora(String accion) {
    return jdbc.queryForList(
        "SELECT id_usuario, valor_anterior ->> 'idCuadrilla' AS anterior,"
            + " valor_nuevo ->> 'idCuadrilla' AS nuevo FROM registro_auditoria"
            + " WHERE entidad = 'ORDEN_TRABAJO' AND id_entidad = ? AND accion = ?",
        ot.getId(),
        accion);
  }

  /** OT correctiva MEDIA de MTOP, generada hace una hora y sin asignar. */
  private OrdenTrabajo guardarOtPendiente() {
    Instant hace1h = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
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
                "Lámpara roja quemada",
                operador.getId()));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    ReglaSLA regla =
        reglaSlaRepository.findByIdOrganismoAndPrioridad(mtop, Prioridad.MEDIA).orElseThrow();
    OrdenTrabajo nueva = incidente.generarOrdenCorrectiva(hace1h, regla);
    incidenteRepository.save(incidente);
    return ordenTrabajoRepository.save(nueva);
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
