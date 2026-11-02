package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ciemsa.sigma.ot.domain.OrdenPausada;
import com.ciemsa.sigma.ot.domain.OrdenReanudada;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
 * Pausar y reanudar la OT (#39) contra PostgreSQL. La OT se arma por los repositorios: correctiva
 * de MTOP con la regla MEDIA de datos de prueba (48 h), asignada a la cuadrilla Norte.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class PausaOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final Duration PLAZO_MEDIA = Duration.ofHours(48);

  @Autowired private WebApplicationContext context;
  @Autowired private ApplicationEvents eventos;
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
  private UsuarioAutenticado tecnicoNorte;
  private UsuarioAutenticado tecnicoSur;
  private OrdenTrabajo ot;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    norte = guardarCuadrilla("Cuadrilla Pausas Norte");
    sur = guardarCuadrilla("Cuadrilla Pausas Sur");
    operador =
        enSesion(guardarUsuario("Operador", "operador.pausas@ciemsa.com", Rol.OPERADOR, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico Norte", "norte.pausas@ciemsa.com", Rol.TECNICO, norte));
    tecnicoSur = enSesion(guardarUsuario("Técnico Sur", "sur.pausas@ciemsa.com", Rol.TECNICO, sur));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-PAUSA-1",
                    "Cruce de pausas",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
    ot = guardarOt(true);
  }

  @AfterEach
  void tearDown() {
    jdbc.update(
        "DELETE FROM pausa_ot WHERE id_orden_trabajo IN"
            + " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)",
        idInstalacion);
    jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", idInstalacion);
    usuarios.forEach(usuarioRepository::deleteById);
    cuadrillas.forEach(cuadrillaRepository::deleteById);
  }

  // --- Reinicio del SLA ---

  @Test
  void pausarYReanudarPorTercero_reiniciaElSlaConElMismoPlazo() throws Exception {
    pausar(operador, ot, "TERCERO", "UTE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_PAUSA"))
        .andExpect(jsonPath("$.pausaAbierta.motivo").value("TERCERO"))
        .andExpect(jsonPath("$.pausaAbierta.detalle").value("UTE"))
        .andExpect(jsonPath("$.vencida").value(false));
    Instant antes = Instant.now().truncatedTo(ChronoUnit.MICROS);

    reanudar(operador, ot)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ASIGNADA"))
        .andExpect(jsonPath("$.pausaAbierta", nullValue()))
        .andExpect(jsonPath("$.pausas.length()").value(1));
    Instant despues = Instant.now();

    OrdenTrabajo leida = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(leida.getInicioSla()).isBetween(antes, despues);
    assertThat(leida.getFechaLimite()).isEqualTo(leida.getInicioSla().plus(PLAZO_MEDIA));

    assertThat(eventos.stream(OrdenPausada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idOrdenTrabajo()).isEqualTo(ot.getId()))
        .satisfies(e -> assertThat(e.motivo()).isEqualTo(MotivoPausa.TERCERO))
        .satisfies(e -> assertThat(e.detalle()).isEqualTo("UTE"))
        .satisfies(e -> assertThat(e.idUsuario()).isEqualTo(operador.getId()));
    assertThat(eventos.stream(OrdenReanudada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.motivo()).isEqualTo(MotivoPausa.TERCERO))
        .satisfies(e -> assertThat(e.fechaLimite()).isEqualTo(leida.getFechaLimite()))
        .satisfies(e -> assertThat(e.idUsuario()).isEqualTo(operador.getId()));
    assertThat(eventos.stream(EstadoOrdenCambiado.class))
        .extracting(EstadoOrdenCambiado::estadoNuevo)
        .containsExactly(EstadoOT.EN_PAUSA, EstadoOT.ASIGNADA);
  }

  @Test
  void pausarYReanudarPorMateriales_laFechaLimiteNoCambia() throws Exception {
    Instant limite = ot.getFechaLimite();

    pausar(operador, ot, "MATERIALES", null).andExpect(status().isOk());
    reanudar(operador, ot).andExpect(status().isOk());

    OrdenTrabajo leida = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(leida.getFechaLimite()).isEqualTo(limite);
    assertThat(eventos.stream(OrdenReanudada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.fechaLimite()).isEqualTo(limite));
  }

  // --- Detalle ---

  @Test
  void obtener_conPausas_devuelveTiemposEHistorial() throws Exception {
    pausar(operador, ot, "CLIMA", "Tormenta").andExpect(status().isOk());
    reanudar(operador, ot).andExpect(status().isOk());
    pausar(operador, ot, "OTRO", "Corte de calle").andExpect(status().isOk());

    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numero").value(ot.getNumero()))
        .andExpect(jsonPath("$.tipo").value("CORRECTIVA"))
        .andExpect(jsonPath("$.idCuadrilla").value(norte.getId()))
        .andExpect(jsonPath("$.slaDefinido").value(true))
        .andExpect(jsonPath("$.pausaAbierta.motivo").value("OTRO"))
        .andExpect(jsonPath("$.pausas.length()").value(2))
        .andExpect(jsonPath("$.pausas[0].motivo").value("CLIMA"))
        .andExpect(jsonPath("$.pausas[0].idUsuarioReanudacion").value(operador.getId()))
        .andExpect(jsonPath("$.tiempos.totalMinutos").isNumber())
        .andExpect(jsonPath("$.tiempos.enPausaMinutos.CLIMA").isNumber())
        .andExpect(jsonPath("$.tiempos.enPausaMinutos.OTRO").isNumber())
        .andExpect(jsonPath("$.tiempos.efectivoMinutos").isNumber());
  }

  @Test
  void obtener_otAnulada_sinTiempos() throws Exception {
    ot.anular("Duplicada", operador.getId(), Instant.now());
    ordenTrabajoRepository.save(ot);

    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADA"))
        .andExpect(jsonPath("$.tiempos", nullValue()));
  }

  @Test
  void obtener_inexistente_devuelve404() throws Exception {
    mvc.perform(get(ORDENES + "/999999").with(user(operador)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  // --- Reglas ---

  @Test
  void reanudar_sinPausa_devuelve400TransicionInvalida() throws Exception {
    reanudar(operador, ot)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"));
  }

  @Test
  void pausar_enPausa_devuelve400TransicionInvalida() throws Exception {
    pausar(operador, ot, "CLIMA", null).andExpect(status().isOk());

    pausar(operador, ot, "MATERIALES", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"));
  }

  @Test
  void pausar_terceroSinDetalle_devuelve400YNoPausa() throws Exception {
    pausar(operador, ot, "TERCERO", " ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));

    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.ASIGNADA);
    assertThat(eventos.stream(OrdenPausada.class)).isEmpty();
  }

  @Test
  void pausar_sinMotivo_devuelve400ConElCampo() throws Exception {
    mvc.perform(
            post(ORDENES + "/" + ot.getId() + "/pausar")
                .with(user(operador))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"detalle\": \"UTE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("motivo")));
  }

  @Test
  void pausar_inexistente_devuelve404() throws Exception {
    mvc.perform(
            post(ORDENES + "/999999/pausar")
                .with(user(operador))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"CLIMA\"}"))
        .andExpect(status().isNotFound());
  }

  // --- Permisos ---

  @Test
  void tecnicoDeLaCuadrilla_puedePausarYReanudar() throws Exception {
    pausar(tecnicoNorte, ot, "FIN_DE_JORNADA", null).andExpect(status().isOk());
    reanudar(tecnicoNorte, ot).andExpect(status().isOk());

    assertThat(eventos.stream(OrdenPausada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idUsuario()).isEqualTo(tecnicoNorte.getId()));
  }

  @Test
  void tecnicoDeOtraCuadrilla_devuelve403() throws Exception {
    pausar(tecnicoSur, ot, "CLIMA", null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(tecnicoSur)))
        .andExpect(status().isForbidden());
  }

  @Test
  void tecnicoConOtPendienteSinCuadrilla_devuelve403() throws Exception {
    OrdenTrabajo pendiente = guardarOt(false);

    pausar(tecnicoNorte, pendiente, "CLIMA", null).andExpect(status().isForbidden());
  }

  @Test
  void tecnicoQueCambioDeCuadrilla_seVerificaContraLaBaseYNoLaSesion() throws Exception {
    // La sesión dice Norte, pero en la base ya está en Sur
    jdbc.update(
        "UPDATE usuario SET id_cuadrilla = ? WHERE id_usuario = ?",
        sur.getId(),
        tecnicoNorte.getId());

    pausar(tecnicoNorte, ot, "CLIMA", null).andExpect(status().isForbidden());
  }

  @Test
  void encargadoDeStock_devuelve403() throws Exception {
    UsuarioAutenticado encargado =
        enSesion(guardarUsuario("Encargado", "stock.pausas@ciemsa.com", Rol.ENCARGADO_STOCK, null));

    pausar(encargado, ot, "CLIMA", null).andExpect(status().isForbidden());
    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(encargado)))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinSesion_devuelve401() throws Exception {
    mvc.perform(get(ORDENES + "/" + ot.getId())).andExpect(status().isUnauthorized());
    mvc.perform(
            post(ORDENES + "/" + ot.getId() + "/pausar")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"CLIMA\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
    mvc.perform(post(ORDENES + "/" + ot.getId() + "/reanudar").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  // --- Auxiliares ---

  private ResultActions pausar(
      UsuarioAutenticado quien, OrdenTrabajo orden, String motivo, String detalle)
      throws Exception {
    String json =
        detalle == null
            ? "{\"motivo\": \"%s\"}".formatted(motivo)
            : "{\"motivo\": \"%s\", \"detalle\": \"%s\"}".formatted(motivo, detalle);
    return mvc.perform(
        post(ORDENES + "/" + orden.getId() + "/pausar")
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions reanudar(UsuarioAutenticado quien, OrdenTrabajo orden) throws Exception {
    return mvc.perform(
        post(ORDENES + "/" + orden.getId() + "/reanudar").with(user(quien)).with(csrf()));
  }

  /** OT correctiva MEDIA de MTOP, generada hace una hora; asignada a Norte si se pide. */
  private OrdenTrabajo guardarOt(boolean asignada) {
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
    if (asignada) {
      nueva.asignar(norte.getId(), LocalDate.now(), hace1h);
    }
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
