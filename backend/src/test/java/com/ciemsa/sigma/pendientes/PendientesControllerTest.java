package com.ciemsa.sigma.pendientes;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.CuadrillaRepository;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
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
 * Tablero de pendientes (#43) contra PostgreSQL. OTs de MTOP con las reglas de datos de prueba
 * (ALTA 8 h, MEDIA 48 h, BAJA 120 h), todas en la instalación TEST-PEND-1: las aserciones filtran
 * por ella para no depender de lo que dejen otros tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PendientesControllerTest {

  private static final String ORDENES = "/api/v1/pendientes/ordenes";
  private static final String EN_ESPERA = "/api/v1/pendientes/incidentes-en-espera";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final String NUMEROS =
      "$.contenido[?(@.codigoInstalacion == 'TEST-PEND-1')].numero";

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
  private Instant ahora;
  private Long mtop;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla cuadrilla;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnico;

  private OrdenTrabajo vencida;
  private OrdenTrabajo media;
  private OrdenTrabajo baja;
  private OrdenTrabajo enPausaPorTercero;
  private OrdenTrabajo sinSla;
  private OrdenTrabajo preventiva;
  private OrdenTrabajo anulada;
  private Incidente enEspera;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    cuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Pendientes", null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.pendientes@ciemsa.com", Rol.JEFATURA, null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.pendientes@ciemsa.com", Rol.OPERADOR, null));
    tecnico =
        enSesion(
            guardarUsuario("Técnico", "tecnico.pendientes@ciemsa.com", Rol.TECNICO, cuadrilla));
    mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-PEND-1",
                    "Cruce de pendientes",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();

    // El orden en que se guardan es el de creación.
    vencida = guardarCorrectiva(Prioridad.ALTA, 10, true, ot -> {}); // venció hace 2 h
    media = guardarCorrectiva(Prioridad.MEDIA, 1, true, ot -> {}); // vence en 47 h
    baja =
        guardarCorrectiva(
            Prioridad.BAJA,
            1,
            true,
            ot -> ot.asignar(cuadrilla.getId(), LocalDate.now(), ahora)); // vence en 119 h
    enPausaPorTercero =
        guardarCorrectiva(
            Prioridad.ALTA,
            9,
            true,
            ot ->
                ot.pausar(MotivoPausa.TERCERO, "UTE", operador.getId(), ahora)); // vencía hace 1 h
    sinSla = guardarCorrectiva(Prioridad.MEDIA, 1, false, ot -> {});
    preventiva =
        ordenTrabajoRepository.save(OrdenTrabajo.preventiva(idInstalacion, LocalDate.now(), ahora));
    anulada =
        guardarCorrectiva(
            Prioridad.BAJA, 1, true, ot -> ot.anular("Duplicada", jefatura.getId(), ahora));

    enEspera = registrarIncidente(Prioridad.MEDIA);
    enEspera.ponerEnEsperaDeTercero("UTE", "Corte de energía");
    enEspera = incidenteRepository.save(enEspera);
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

  // --- Orden ---

  @Test
  void porVencimiento_primeroLasQueCorrenDespuesPausadasPorTerceroYAlFinalSinSla()
      throws Exception {
    consultar(jefatura, null)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                NUMEROS,
                contains(
                    vencida.getNumero(),
                    media.getNumero(),
                    baja.getNumero(),
                    enPausaPorTercero.getNumero(),
                    sinSla.getNumero(),
                    preventiva.getNumero())));
  }

  @Test
  void porPrioridad_altaMediaBajaYAlFinalLasPreventivas() throws Exception {
    consultar(jefatura, "PRIORIDAD")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                NUMEROS,
                contains(
                    vencida.getNumero(),
                    enPausaPorTercero.getNumero(),
                    media.getNumero(),
                    sinSla.getNumero(),
                    baja.getNumero(),
                    preventiva.getNumero())));
  }

  @Test
  void porCreacion_deLaMasViejaALaMasNueva() throws Exception {
    consultar(jefatura, "CREACION")
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                NUMEROS,
                contains(
                    vencida.getNumero(),
                    media.getNumero(),
                    baja.getNumero(),
                    enPausaPorTercero.getNumero(),
                    sinSla.getNumero(),
                    preventiva.getNumero())));
  }

  // --- Filtro por estado (#72) ---

  @Test
  void porEstadoPendiente_soloLasPendientesEnElOrdenPedido() throws Exception {
    mvc.perform(get(ORDENES).param("size", "100").param("estado", "PENDIENTE").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                NUMEROS,
                contains(
                    vencida.getNumero(),
                    media.getNumero(),
                    sinSla.getNumero(),
                    preventiva.getNumero())));
  }

  @Test
  void porEstadoEnPausa_soloLaPausada() throws Exception {
    mvc.perform(get(ORDENES).param("size", "100").param("estado", "EN_PAUSA").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(NUMEROS, contains(enPausaPorTercero.getNumero())));
  }

  @Test
  void porEstadoNoAbierto_paginaVacia() throws Exception {
    mvc.perform(get(ORDENES).param("estado", "ANULADA").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void estadoInvalido_400() throws Exception {
    mvc.perform(get(ORDENES).param("estado", "OTRO").with(user(jefatura)))
        .andExpect(status().isBadRequest());
  }

  // --- Datos de cada fila ---

  @Test
  void vencidaYSinSla_seMarcan() throws Exception {
    consultar(jefatura, null)
        .andExpect(jsonPath(campo(vencida, "vencida"), contains(true)))
        .andExpect(jsonPath(campo(vencida, "slaDefinido"), contains(true)))
        .andExpect(jsonPath(campo(media, "vencida"), contains(false)))
        .andExpect(jsonPath(campo(sinSla, "slaDefinido"), contains(false)))
        .andExpect(jsonPath(campo(sinSla, "vencida"), contains(false)));
  }

  @Test
  void enPausaPorTercero_muestraElMotivoYNoVenceAunqueLaFechaLimitePaso() throws Exception {
    consultar(jefatura, null)
        .andExpect(jsonPath(campo(enPausaPorTercero, "estado"), contains("EN_PAUSA")))
        .andExpect(jsonPath(campo(enPausaPorTercero, "estadoAnteriorPausa"), contains("PENDIENTE")))
        .andExpect(jsonPath(campo(enPausaPorTercero, "pausa.motivo"), contains("TERCERO")))
        .andExpect(jsonPath(campo(enPausaPorTercero, "pausa.detalle"), contains("UTE")))
        .andExpect(jsonPath(campo(enPausaPorTercero, "vencida"), contains(false)));
  }

  @Test
  void cadaFila_traeInstalacionCuadrillaEIncidente() throws Exception {
    consultar(jefatura, null)
        .andExpect(jsonPath(campo(baja, "nombreInstalacion"), contains("Cruce de pendientes")))
        .andExpect(jsonPath(campo(baja, "cuadrilla"), contains("Cuadrilla Pendientes")))
        .andExpect(jsonPath(campo(baja, "estado"), contains("ASIGNADA")))
        .andExpect(
            jsonPath(campo(baja, "numeroIncidente"), contains(baja.getIncidente().getNumero())))
        .andExpect(jsonPath(campo(preventiva, "tipo"), contains("PREVENTIVA")));
  }

  @Test
  void paginado_respetaElTamanio() throws Exception {
    mvc.perform(get(ORDENES).param("size", "2").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(2))
        .andExpect(jsonPath("$.tamanio").value(2))
        .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(6)));
  }

  @Test
  void ordenInvalido_400() throws Exception {
    consultar(jefatura, "OTRO").andExpect(status().isBadRequest());
  }

  // --- Incidentes en espera de tercero ---

  @Test
  void incidentesEnEspera_listaLosDeTerceroConSuCausa() throws Exception {
    String delTest = "$[?(@.codigoInstalacion == 'TEST-PEND-1')]";
    mvc.perform(get(EN_ESPERA).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(delTest + ".numero", contains(enEspera.getNumero())))
        .andExpect(jsonPath(delTest + ".tercero", contains("UTE")))
        .andExpect(jsonPath(delTest + ".causa", contains("Corte de energía")));
  }

  // --- Permisos ---

  @Test
  void operador_puedeVerElTablero() throws Exception {
    consultar(operador, null).andExpect(status().isOk());
  }

  @Test
  void tecnico_403() throws Exception {
    consultar(tecnico, null).andExpect(status().isForbidden());
    mvc.perform(get(EN_ESPERA).with(user(tecnico))).andExpect(status().isForbidden());
  }

  // --- Ayudas ---

  private ResultActions consultar(UsuarioAutenticado quien, String orden) throws Exception {
    var pedido = get(ORDENES).param("size", "100").with(user(quien));
    if (orden != null) {
      pedido.param("orden", orden);
    }
    return mvc.perform(pedido);
  }

  /** JsonPath de un campo de la fila de esa OT: devuelve una lista de un elemento. */
  private static String campo(OrdenTrabajo ot, String campo) {
    return "$.contenido[?(@.numero == '" + ot.getNumero() + "')]." + campo;
  }

  private OrdenTrabajo guardarCorrectiva(
      Prioridad prioridad, int horasAtras, boolean conSla, Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente = registrarIncidente(prioridad);
    ReglaSLA regla =
        conSla
            ? reglaSlaRepository.findByIdOrganismoAndPrioridad(mtop, prioridad).orElseThrow()
            : null;
    OrdenTrabajo ot =
        incidente.generarOrdenCorrectiva(ahora.minus(Duration.ofHours(horasAtras)), regla);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    return ordenTrabajoRepository.save(ot);
  }

  private Incidente registrarIncidente(Prioridad prioridad) {
    return incidenteRepository.save(
        Incidente.registrar(
            idInstalacion,
            OrigenIncidente.MONITOREO,
            idCategoria,
            prioridad,
            "Falla de prueba",
            operador.getId()));
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
