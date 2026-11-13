package com.ciemsa.sigma.pendientes;

import static org.hamcrest.Matchers.nullValue;
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
import java.time.ZoneId;
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
 * Resumen del inicio por rol (#72) contra PostgreSQL. Reglas de prueba: ALTA 8 h, MEDIA 48 h. Los
 * contadores son de toda la base: los demás tests de integración borran lo que crean.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResumenInicioTest {

  private static final String RESUMEN = "/api/v1/inicio/resumen";
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
  private Instant ahora;
  private LocalDate hoy;
  private Long mtop;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla norte;
  private Cuadrilla sur;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnicoNorte;
  private UsuarioAutenticado encargado;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    hoy = LocalDate.now(ZoneId.of("America/Montevideo"));
    norte = guardarCuadrilla("Cuadrilla Inicio Norte");
    sur = guardarCuadrilla("Cuadrilla Inicio Sur");
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.inicio@ciemsa.com", Rol.JEFATURA, null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.inicio@ciemsa.com", Rol.OPERADOR, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico", "tecnico.inicio@ciemsa.com", Rol.TECNICO, norte));
    encargado =
        enSesion(guardarUsuario("Encargado", "stock.inicio@ciemsa.com", Rol.ENCARGADO_STOCK, null));
    mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-INICIO-1",
                    "Cruce del inicio",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();

    // Pendiente y vencida (venció hace 2 h).
    guardarOt(Prioridad.ALTA, 10, ot -> {});
    // Por vencer (en 7 h), asignada a Norte para hoy.
    guardarOt(Prioridad.ALTA, 1, ot -> ot.asignar(norte.getId(), hoy, ahora));
    // En pausa por un tercero: la fecha límite pasó, pero no vence.
    guardarOt(
        Prioridad.ALTA, 10, ot -> ot.pausar(MotivoPausa.TERCERO, "UTE", operador.getId(), ahora));
    // En pausa por clima: el plazo sigue corriendo, así que sí está vencida.
    guardarOt(
        Prioridad.ALTA, 10, ot -> ot.pausar(MotivoPausa.CLIMA, null, operador.getId(), ahora));
    // Vence en 47 h, asignada a Norte para mañana: ni por vencer ni de hoy.
    guardarOt(Prioridad.MEDIA, 1, ot -> ot.asignar(norte.getId(), hoy.plusDays(1), ahora));
    // De Sur para hoy: no es de la cuadrilla del técnico.
    guardarOt(Prioridad.MEDIA, 1, ot -> ot.asignar(sur.getId(), hoy, ahora));
    // Anulada con la fecha límite pasada: no cuenta como vencida.
    guardarOt(Prioridad.ALTA, 10, ot -> ot.anular("Duplicada", jefatura.getId(), ahora));
    // Un incidente en espera de tercero.
    Incidente enEspera = registrarIncidente(Prioridad.MEDIA);
    enEspera.ponerEnEsperaDeTercero("UTE", "Corte de energía");
    incidenteRepository.save(enEspera);
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
  void jefatura_pendientesVencidasPorVencerEnPausaYEnEspera() throws Exception {
    resumen(jefatura)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pendientesDeAsignar").value(1))
        .andExpect(jsonPath("$.vencidas").value(2))
        .andExpect(jsonPath("$.porVencer").value(1))
        .andExpect(jsonPath("$.enPausa").value(2))
        .andExpect(jsonPath("$.incidentesEnEspera").value(1))
        .andExpect(jsonPath("$.incidentesHoy").value(nullValue()))
        .andExpect(jsonPath("$.otsDeHoy").value(nullValue()));
  }

  @Test
  void operador_vencidasEnEsperaEIncidentesDeHoy() throws Exception {
    // 7 incidentes con OT más el que está en espera, todos registrados ahora.
    resumen(operador)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vencidas").value(2))
        .andExpect(jsonPath("$.incidentesEnEspera").value(1))
        .andExpect(jsonPath("$.incidentesHoy").value(8))
        .andExpect(jsonPath("$.pendientesDeAsignar").value(nullValue()))
        .andExpect(jsonPath("$.otsDeHoy").value(nullValue()));
  }

  @Test
  void tecnico_soloLasDeSuCuadrillaParaHoy() throws Exception {
    resumen(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.otsDeHoy").value(1))
        .andExpect(jsonPath("$.vencidas").value(nullValue()))
        .andExpect(jsonPath("$.pendientesDeAsignar").value(nullValue()));
  }

  @Test
  void encargadoDeStock_sinTarjetas() throws Exception {
    resumen(encargado)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vencidas").value(nullValue()))
        .andExpect(jsonPath("$.otsDeHoy").value(nullValue()));
  }

  @Test
  void sinSesion_401() throws Exception {
    mvc.perform(get(RESUMEN)).andExpect(status().isUnauthorized());
  }

  // --- Ayudas ---

  private ResultActions resumen(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(get(RESUMEN).with(user(quien)));
  }

  private void guardarOt(Prioridad prioridad, int horasAtras, Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente = registrarIncidente(prioridad);
    ReglaSLA regla =
        reglaSlaRepository.findByIdOrganismoAndPrioridad(mtop, prioridad).orElseThrow();
    OrdenTrabajo ot =
        incidente.generarOrdenCorrectiva(ahora.minus(Duration.ofHours(horasAtras)), regla);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    ordenTrabajoRepository.save(ot);
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
