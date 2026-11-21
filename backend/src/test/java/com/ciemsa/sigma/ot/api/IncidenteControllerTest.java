package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.CuadrillaRepository;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import com.ciemsa.sigma.ot.domain.IncidenteCerradoSinOt;
import com.ciemsa.sigma.ot.domain.IncidenteRegistrado;
import com.ciemsa.sigma.ot.domain.OrdenGenerada;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
 * Registrar incidente (CU-1, HU-03) contra PostgreSQL. Las categorías y las reglas de SLA de MTOP
 * vienen de R__catalogos_prueba.sql (MEDIA: 48 h de resolución).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class IncidenteControllerTest {

  private static final String INCIDENTES = "/api/v1/incidentes";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final Duration RESOLUCION_MEDIA = Duration.ofHours(48);

  @Autowired private WebApplicationContext context;
  @Autowired private ApplicationEvents eventos;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private final List<Long> instalaciones = new ArrayList<>();
  private Long idCuadrilla;
  private Long idOrganismoSinReglas;
  private Long idCategoriaInactiva;

  private UsuarioAutenticado operador;
  private Long idInstalacion;
  private Long idLampara;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    operador = enSesion(guardarUsuario("Operador Test", "operador.inc@ciemsa.com", Rol.OPERADOR));
    Long mtop =
        jdbc.queryForObject("SELECT id_organismo FROM organismo WHERE nombre = 'MTOP'", Long.class);
    idInstalacion = guardarInstalacion("TEST-INC-1", mtop);
    idLampara = idCategoria("Lámpara quemada");
  }

  @AfterEach
  void tearDown() {
    for (Long id : instalaciones) {
      jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", id);
      jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", id);
      jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", id);
    }
    if (idOrganismoSinReglas != null) {
      jdbc.update("DELETE FROM organismo WHERE id_organismo = ?", idOrganismoSinReglas);
    }
    if (idCategoriaInactiva != null) {
      jdbc.update("DELETE FROM categoria_falla WHERE id_categoria_falla = ?", idCategoriaInactiva);
    }
    usuarios.forEach(usuarioRepository::deleteById);
    if (idCuadrilla != null) {
      cuadrillaRepository.deleteById(idCuadrilla);
    }
  }

  // --- Registrar ---

  @Test
  void registrar_conRegla_devuelve201ConLaOtPendienteYSuVencimiento() throws Exception {
    Instant antes = Instant.now().truncatedTo(ChronoUnit.MICROS);

    String json =
        registrar(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", null))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", startsWith(INCIDENTES + "/")))
            .andExpect(jsonPath("$.numero", startsWith("INC-")))
            .andExpect(jsonPath("$.estado").value("CON_OT"))
            .andExpect(jsonPath("$.prioridad").value("MEDIA"))
            .andExpect(jsonPath("$.ordenTrabajo.numero", startsWith("OT-")))
            .andExpect(jsonPath("$.ordenTrabajo.slaDefinido").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

    OrdenTrabajo ot = otDe(json);
    assertThat(ot.getEstado().name()).isEqualTo("PENDIENTE");
    assertThat(ot.getInicioSla()).isAfterOrEqualTo(antes);
    assertThat(ot.getFechaLimite()).isEqualTo(ot.getInicioSla().plus(RESOLUCION_MEDIA));
    Long idIncidente = idDe(json);
    assertThat(eventos.stream(IncidenteRegistrado.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idIncidente()).isEqualTo(idIncidente))
        .satisfies(e -> assertThat(e.idUsuario()).isEqualTo(operador.getId()));
    assertThat(eventos.stream(OrdenGenerada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idOrdenTrabajo()).isEqualTo(ot.getId()))
        .satisfies(e -> assertThat(e.idIncidente()).isEqualTo(idIncidente));
  }

  @Test
  void registrar_sinPrioridad_usaLaSugeridaPorLaCategoria() throws Exception {
    registrar(cuerpo(idInstalacion, idCategoria("Semáforo apagado"), null, null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.prioridad").value("ALTA"));
  }

  @Test
  void registrar_sinReglaParaElOrganismo_otConSlaSinDefinir() throws Exception {
    idOrganismoSinReglas =
        jdbc.queryForObject(
            "INSERT INTO organismo (nombre) VALUES ('Organismo sin reglas test')"
                + " RETURNING id_organismo",
            Long.class);
    Long instalacionSinReglas = guardarInstalacion("TEST-INC-2", idOrganismoSinReglas);

    registrar(cuerpo(instalacionSinReglas, idLampara, "\"MEDIA\"", null))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("CON_OT"))
        .andExpect(jsonPath("$.ordenTrabajo.numero", startsWith("OT-")))
        .andExpect(jsonPath("$.ordenTrabajo.fechaLimite", nullValue()))
        .andExpect(jsonPath("$.ordenTrabajo.slaDefinido").value(false));
  }

  @Test
  void registrar_fallaDeTercero_quedaEnEsperaSinOt() throws Exception {
    registrar(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", "UTE"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.estado").value("EN_ESPERA_TERCERO"))
        .andExpect(jsonPath("$.tercero").value("UTE"))
        .andExpect(jsonPath("$.causa").value("Corte de energía"))
        .andExpect(jsonPath("$.ordenTrabajo", nullValue()));

    assertThat(eventos.stream(IncidenteRegistrado.class)).hasSize(1);
    assertThat(eventos.stream(OrdenGenerada.class)).isEmpty();
  }

  // --- 3a y 4a ---

  @Test
  void registrar_instalacionInexistente_devuelve400() throws Exception {
    registrar(cuerpo(999_999L, idLampara, "\"MEDIA\"", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
    assertThat(eventos.stream(IncidenteRegistrado.class)).isEmpty();
  }

  @Test
  void registrar_instalacionInactiva_devuelve400() throws Exception {
    jdbc.update("UPDATE instalacion SET activa = FALSE WHERE id_instalacion = ?", idInstalacion);

    registrar(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void registrar_categoriaInexistente_devuelve400() throws Exception {
    registrar(cuerpo(idInstalacion, 999_999L, "\"MEDIA\"", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void registrar_categoriaInactiva_devuelve400() throws Exception {
    idCategoriaInactiva =
        jdbc.queryForObject(
            "INSERT INTO categoria_falla (nombre, prioridad_sugerida, activa)"
                + " VALUES ('Categoría inactiva test', 'BAJA', FALSE) RETURNING id_categoria_falla",
            Long.class);

    registrar(cuerpo(idInstalacion, idCategoriaInactiva, "\"BAJA\"", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void registrar_categoriaOtraSinDetalle_devuelve400() throws Exception {
    String json =
        """
        {"idInstalacion": %d, "origen": "MONITOREO", "idCategoriaFalla": %d,
         "descripcion": "Falla"}
        """
            .formatted(idInstalacion, idCategoria("Otra"));

    registrar(json)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void registrar_categoriaOtraConDetalle_devuelve201() throws Exception {
    String json =
        """
        {"idInstalacion": %d, "origen": "MONITOREO", "idCategoriaFalla": %d,
         "descripcion": "Poste del semáforo inclinado por un choque"}
        """
            .formatted(idInstalacion, idCategoria("Otra"));

    registrar(json).andExpect(status().isCreated());
  }

  @Test
  void registrar_sinCamposObligatorios_devuelve400ConLosCampos() throws Exception {
    registrar("{\"descripcion\": \" \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("idInstalacion")))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("descripcion")));
  }

  // --- Consultar ---

  @Test
  void obtener_devuelveElIncidenteConSuOt() throws Exception {
    String json = registrarYLeer(null);

    mvc.perform(get(INCIDENTES + "/" + idDe(json)).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.numero").value(JsonPath.<String>read(json, "$.numero")))
        .andExpect(jsonPath("$.estado").value("CON_OT"))
        .andExpect(
            jsonPath("$.ordenTrabajo.numero")
                .value(JsonPath.<String>read(json, "$.ordenTrabajo.numero")));
  }

  @Test
  void obtener_inexistente_devuelve404() throws Exception {
    mvc.perform(get(INCIDENTES + "/999999").with(user(operador)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  // --- Resolver la espera (4b) ---

  @Test
  void cerrarSinOt_desdeLaEspera_quedaCerradoYPublicaElEvento() throws Exception {
    Long id = idDe(registrarYLeer("UTE"));

    accion(id, "cerrar-sin-ot")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CERRADO_SIN_OT"))
        .andExpect(jsonPath("$.ordenTrabajo", nullValue()));

    assertThat(eventos.stream(IncidenteCerradoSinOt.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idIncidente()).isEqualTo(id))
        .satisfies(e -> assertThat(e.idUsuario()).isEqualTo(operador.getId()));
    assertThat(ordenTrabajoRepository.findByIncidenteId(id)).isEmpty();
  }

  @Test
  void generarOt_desdeLaEspera_cuentaElSlaDesdeEseMomento() throws Exception {
    Long id = idDe(registrarYLeer("UTE"));
    Instant antes = Instant.now().truncatedTo(ChronoUnit.MICROS);

    accion(id, "generar-ot")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CON_OT"))
        .andExpect(jsonPath("$.ordenTrabajo.numero", startsWith("OT-")))
        .andExpect(jsonPath("$.ordenTrabajo.slaDefinido").value(true));
    Instant despues = Instant.now();

    OrdenTrabajo ot = ordenTrabajoRepository.findByIncidenteId(id).orElseThrow();
    assertThat(ot.getInicioSla()).isBetween(antes, despues);
    assertThat(ot.getFechaLimite()).isEqualTo(ot.getInicioSla().plus(RESOLUCION_MEDIA));
    assertThat(eventos.stream(OrdenGenerada.class))
        .singleElement()
        .satisfies(e -> assertThat(e.idOrdenTrabajo()).isEqualTo(ot.getId()));
  }

  @Test
  void generarOt_incidenteConOt_devuelve400() throws Exception {
    Long id = idDe(registrarYLeer(null));

    accion(id, "generar-ot")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void cerrarSinOt_incidenteConOt_devuelve400() throws Exception {
    Long id = idDe(registrarYLeer(null));

    accion(id, "cerrar-sin-ot")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void cerrarSinOt_yaCerrado_devuelve400() throws Exception {
    Long id = idDe(registrarYLeer("UTE"));
    accion(id, "cerrar-sin-ot").andExpect(status().isOk());

    accion(id, "generar-ot")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void acciones_incidenteInexistente_devuelven404() throws Exception {
    accion(999_999L, "cerrar-sin-ot").andExpect(status().isNotFound());
    accion(999_999L, "generar-ot").andExpect(status().isNotFound());
  }

  // --- Seguridad: Operador y Jefatura ---

  @Test
  void registrar_conRolJefatura_devuelve201() throws Exception {
    UsuarioAutenticado jefa =
        enSesion(guardarUsuario("Jefa Test", "jefa.inc@ciemsa.com", Rol.JEFATURA));

    mvc.perform(
            post(INCIDENTES)
                .with(user(jefa))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", null)))
        .andExpect(status().isCreated());
  }

  @Test
  void conRolTecnico_losCuatroEndpointsDevuelven403() throws Exception {
    Long id = idDe(registrarYLeer("UTE"));
    idCuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Inc Test", null)).getId();
    Usuario tecnico =
        usuarioRepository.save(
            Usuario.crear(
                "Técnico Test",
                "tecnico.inc@ciemsa.com",
                HASH,
                Rol.TECNICO,
                cuadrillaRepository.findById(idCuadrilla).orElseThrow()));
    usuarios.add(tecnico.getId());
    UsuarioAutenticado enSesion = enSesion(tecnico);

    mvc.perform(
            post(INCIDENTES)
                .with(user(enSesion))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", null)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
    mvc.perform(get(INCIDENTES + "/" + id).with(user(enSesion))).andExpect(status().isForbidden());
    mvc.perform(post(INCIDENTES + "/" + id + "/cerrar-sin-ot").with(user(enSesion)).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(post(INCIDENTES + "/" + id + "/generar-ot").with(user(enSesion)).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinSesion_devuelve401() throws Exception {
    mvc.perform(
            post(INCIDENTES)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", null)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
    mvc.perform(get(INCIDENTES + "/1")).andExpect(status().isUnauthorized());
    mvc.perform(post(INCIDENTES + "/1/cerrar-sin-ot").with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(post(INCIDENTES + "/1/generar-ot").with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  // --- Auxiliares ---

  private ResultActions registrar(String json) throws Exception {
    return mvc.perform(
        post(INCIDENTES)
            .with(user(operador))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** Registra un incidente MEDIA sobre la instalación de MTOP; con tercero queda en espera. */
  private String registrarYLeer(String tercero) throws Exception {
    String json =
        registrar(cuerpo(idInstalacion, idLampara, "\"MEDIA\"", tercero))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    eventos.clear();
    return json;
  }

  private ResultActions accion(Long idIncidente, String accion) throws Exception {
    return mvc.perform(
        post(INCIDENTES + "/" + idIncidente + "/" + accion).with(user(operador)).with(csrf()));
  }

  private static String cuerpo(
      Long idInstalacion, Long idCategoria, String prioridad, String tercero) {
    String fallaDeTercero =
        tercero == null
            ? ""
            : ", \"tercero\": \"" + tercero + "\", \"causa\": \"Corte de energía\"";
    return """
        {"idInstalacion": %d, "origen": "RECLAMO_ORGANISMO", "idCategoriaFalla": %d,
         "prioridad": %s, "descripcion": "Lámpara roja quemada"%s}
        """
        .formatted(idInstalacion, idCategoria, prioridad, fallaDeTercero);
  }

  private static Long idDe(String json) {
    return ((Number) JsonPath.read(json, "$.id")).longValue();
  }

  private OrdenTrabajo otDe(String json) {
    return ordenTrabajoRepository.findByIncidenteId(idDe(json)).orElseThrow();
  }

  private Long idCategoria(String nombre) {
    return jdbc.queryForObject(
        "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = ?", Long.class, nombre);
  }

  private Long guardarInstalacion(String codigo, Long idOrganismo) {
    Long id =
        instalacionRepository
            .save(
                Instalacion.crear(
                    codigo,
                    "Cruce " + codigo,
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    idOrganismo))
            .getId();
    instalaciones.add(id);
    return id;
  }

  private Usuario guardarUsuario(String nombre, String correo, Rol rol) {
    Usuario usuario = usuarioRepository.save(Usuario.crear(nombre, correo, HASH, rol, null));
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
