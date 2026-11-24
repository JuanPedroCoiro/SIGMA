package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * El técnico se cambia de cuadrilla (#94, HU-20) contra PostgreSQL: la consulta previa con el
 * aviso, el cambio, la bitácora y la bandeja «Mis OTs» después del cambio.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MiCuadrillaControllerTest {

  private static final String MI_CUADRILLA = "/api/v1/mi-cuadrilla";
  private static final String HASH = "$2a$10$hashDePrueba";

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
  private LocalDate hoy;

  private Cuadrilla norte;
  private Cuadrilla sur;
  private Cuadrilla inactiva;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado tecnico;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    hoy = LocalDate.now();
    norte = guardarCuadrilla("Cuadrilla Cambio Norte");
    sur = guardarCuadrilla("Cuadrilla Cambio Sur");
    inactiva = guardarCuadrilla("Cuadrilla Cambio Inactiva");
    inactiva.desactivar(0);
    cuadrillaRepository.save(inactiva);
    operador =
        enSesion(guardarUsuario("Operador", "operador.cambio@ciemsa.com", Rol.OPERADOR, null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.cambio@ciemsa.com", Rol.JEFATURA, null));
    tecnico = enSesion(guardarUsuario("Técnico", "tecnico.cambio@ciemsa.com", Rol.TECNICO, norte));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-CAMBIO-1",
                    "Cruce del cambio de cuadrilla",
                    "Ruta 9",
                    new BigDecimal("-34.800000"),
                    new BigDecimal("-55.900000"),
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
    cuadrillas.forEach(cuadrillaRepository::deleteById);
  }

  // --- Consulta previa ---

  @Test
  void consultar_traeLaActualElAvisoYSoloLasActivas() throws Exception {
    guardarOt(norte, ot -> {}); // asignada
    guardarOt(norte, this::iniciar); // en proceso
    guardarOt(
        norte,
        ot -> {
          iniciar(ot);
          ot.pausar(MotivoPausa.TERCERO, "UTE", tecnico.getId(), Instant.now());
        }); // en pausa
    guardarOt(
        norte,
        ot -> {
          iniciar(ot);
          ot.cerrarTecnicamente(
              new CierreTecnico("Diagnóstico", "Solución", null), UUID.randomUUID(), Instant.now());
        }); // cerrada: no cuenta
    guardarOt(sur, this::iniciar); // de otra cuadrilla: no cuenta

    consultar(tecnico)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actual.id").value(norte.getId()))
        .andExpect(jsonPath("$.actual.nombre").value("Cuadrilla Cambio Norte"))
        .andExpect(jsonPath("$.otsAbiertas").value(3))
        .andExpect(jsonPath("$.otsEnCurso").value(2))
        .andExpect(jsonPath("$.activas[*].id").value(hasItem(norte.getId().intValue())))
        .andExpect(jsonPath("$.activas[*].id").value(hasItem(sur.getId().intValue())))
        .andExpect(jsonPath("$.activas[*].id").value(not(hasItem(inactiva.getId().intValue()))));
  }

  @Test
  void consultar_sinOts_noAvisa() throws Exception {
    consultar(tecnico)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsAbiertas").value(0))
        .andExpect(jsonPath("$.otsEnCurso").value(0));
  }

  // --- Cambio ---

  @Test
  void cambiar_aOtraActiva_cambiaLaCuadrillaYRespondeConLaNueva() throws Exception {
    guardarOt(sur, ot -> {});

    cambiar(tecnico, sur.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actual.id").value(sur.getId()))
        .andExpect(jsonPath("$.otsAbiertas").value(1))
        .andExpect(jsonPath("$.otsEnCurso").value(0));

    assertThat(idCuadrillaEnLaBase()).isEqualTo(sur.getId());
  }

  @Test
  void cambiar_quedaEnLaBitacoraConQuienYDeCualACual() throws Exception {
    cambiar(tecnico, sur.getId()).andExpect(status().isOk());

    List<Map<String, Object>> registros = bitacora();
    assertThat(registros).hasSize(1);
    assertThat(registros.getFirst())
        .containsEntry("id_usuario", tecnico.getId())
        .containsEntry("anterior", String.valueOf(norte.getId()))
        .containsEntry("nuevo", String.valueOf(sur.getId()));
  }

  @Test
  void cambiar_aLaMisma_noCambiaNadaNiVaALaBitacora() throws Exception {
    cambiar(tecnico, norte.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actual.id").value(norte.getId()));

    assertThat(idCuadrillaEnLaBase()).isEqualTo(norte.getId());
    assertThat(bitacora()).isEmpty();
  }

  @Test
  void cambiar_yVolverALaSuya_dejaDosRegistros() throws Exception {
    cambiar(tecnico, sur.getId()).andExpect(status().isOk());
    cambiar(tecnico, norte.getId()).andExpect(status().isOk());

    assertThat(idCuadrillaEnLaBase()).isEqualTo(norte.getId());
    assertThat(bitacora()).hasSize(2);
  }

  @Test
  void cambiar_aUnaInactiva_devuelve400YNoCambia() throws Exception {
    cambiar(tecnico, inactiva.getId())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("La cuadrilla Cuadrilla Cambio Inactiva está inactiva."));

    assertThat(idCuadrillaEnLaBase()).isEqualTo(norte.getId());
    assertThat(bitacora()).isEmpty();
  }

  @Test
  void cambiar_aUnaInexistente_devuelve404() throws Exception {
    cambiar(tecnico, 999_999L).andExpect(status().isNotFound());

    assertThat(idCuadrillaEnLaBase()).isEqualTo(norte.getId());
  }

  @Test
  void cambiar_sinCuadrilla_devuelve400() throws Exception {
    mvc.perform(
            post(MI_CUADRILLA + "/cambiar")
                .with(user(tecnico))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCuadrilla\": null}"))
        .andExpect(status().isBadRequest());
  }

  // --- Las OTs y la bandeja ---

  @Test
  void despuesDelCambio_misOtsMuestraLasDeLaNuevaYLasDeLaAnteriorNoSeTocan() throws Exception {
    OrdenTrabajo deNorte = guardarOt(norte, this::iniciar);
    OrdenTrabajo deSur = guardarOt(sur, ot -> {});

    cambiar(tecnico, sur.getId()).andExpect(status().isOk());

    mvc.perform(get("/api/v1/mis-ots").with(user(tecnico)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[*].id").value(contains(deSur.getId().intValue())));
    OrdenTrabajo norteDespues = ordenTrabajoRepository.findById(deNorte.getId()).orElseThrow();
    assertThat(norteDespues.getIdCuadrilla()).isEqualTo(norte.getId());
    assertThat(norteDespues.getEstado()).isEqualTo(deNorte.getEstado());
  }

  // --- Permisos ---

  @Test
  void operadorYJefatura_devuelven403() throws Exception {
    consultar(operador)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
    consultar(jefatura).andExpect(status().isForbidden());
    cambiar(operador, sur.getId()).andExpect(status().isForbidden());
    cambiar(jefatura, sur.getId()).andExpect(status().isForbidden());
  }

  @Test
  void sinSesion_devuelve401() throws Exception {
    mvc.perform(get(MI_CUADRILLA)).andExpect(status().isUnauthorized());
  }

  @Test
  void elCuerpoNoEligeUsuario_siempreCambiaAlActual() throws Exception {
    Usuario otro = guardarUsuario("Otro", "otro.cambio@ciemsa.com", Rol.TECNICO, norte);

    mvc.perform(
            post(MI_CUADRILLA + "/cambiar")
                .with(user(tecnico))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"idCuadrilla\": %d, \"idUsuario\": %d}"
                        .formatted(sur.getId(), otro.getId())))
        .andExpect(status().isOk());

    assertThat(idCuadrillaEnLaBase()).isEqualTo(sur.getId());
    assertThat(
            jdbc.queryForObject(
                "SELECT id_cuadrilla FROM usuario WHERE id_usuario = ?", Long.class, otro.getId()))
        .isEqualTo(norte.getId());
  }

  // --- Auxiliares ---

  private ResultActions consultar(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(get(MI_CUADRILLA).with(user(quien)));
  }

  private ResultActions cambiar(UsuarioAutenticado quien, Long idCuadrilla) throws Exception {
    return mvc.perform(
        post(MI_CUADRILLA + "/cambiar")
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idCuadrilla\": %d}".formatted(idCuadrilla)));
  }

  private Long idCuadrillaEnLaBase() {
    return jdbc.queryForObject(
        "SELECT id_cuadrilla FROM usuario WHERE id_usuario = ?", Long.class, tecnico.getId());
  }

  /** Los cambios de cuadrilla del técnico: quién y la cuadrilla anterior y nueva (como texto). */
  private List<Map<String, Object>> bitacora() {
    return jdbc.queryForList(
        "SELECT id_usuario, valor_anterior ->> 'idCuadrilla' AS anterior,"
            + " valor_nuevo ->> 'idCuadrilla' AS nuevo FROM registro_auditoria"
            + " WHERE entidad = 'USUARIO' AND id_entidad = ? AND accion = 'CAMBIO_CUADRILLA'"
            + " ORDER BY id_registro_auditoria",
        tecnico.getId());
  }

  private void iniciar(OrdenTrabajo ot) {
    ot.iniciar(Instant.now());
  }

  /** OT correctiva MEDIA de MTOP asignada a {@code cuadrilla} y llevada a su estado. */
  private OrdenTrabajo guardarOt(Cuadrilla cuadrilla, Consumer<OrdenTrabajo> preparar) {
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
    nueva.asignar(cuadrilla.getId(), hoy, hace1h);
    preparar.accept(nueva);
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
