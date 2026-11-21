package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
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
import com.ciemsa.sigma.ot.domain.OrdenAsignada;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
 * Asignar y reasignar OTs (#41, HU-05) contra PostgreSQL. Las OTs son correctivas sin SLA en la
 * instalación TEST-ASIGNAR-1; cada test arma las suyas en el estado que necesita.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class AsignacionOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String HASH = "$2a$10$hashDePrueba";

  /** «Hoy» como lo calcula el servicio: en Montevideo. */
  private static final ZoneId MONTEVIDEO = ZoneId.of("America/Montevideo");

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
  private final List<Long> cuadrillas = new ArrayList<>();
  private Instant ahora;
  private LocalDate hoy;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla norte;
  private Cuadrilla sur;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnico;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    hoy = LocalDate.now(MONTEVIDEO);
    norte = guardarCuadrilla("Cuadrilla Asignar Norte");
    sur = guardarCuadrilla("Cuadrilla Asignar Sur");
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.asignar@ciemsa.com", Rol.JEFATURA, null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.asignar@ciemsa.com", Rol.OPERADOR, null));
    tecnico = enSesion(guardarUsuario("Técnico", "tecnico.asignar@ciemsa.com", Rol.TECNICO, norte));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-ASIGNAR-1",
                    "Cruce de asignar",
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
    cuadrillas.forEach(cuadrillaRepository::deleteById);
  }

  // --- Asignar y reasignar ---

  @Test
  void variasPendientes_lasAsignaConSuTramoYPublicaOrdenAsignada() throws Exception {
    OrdenTrabajo ot1 = guardarOt(o -> {});
    OrdenTrabajo ot2 = guardarOt(o -> {});

    asignar(jefatura, List.of(ot1.getId(), ot2.getId()), norte.getId(), hoy)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].estado", contains("ASIGNADA", "ASIGNADA")))
        .andExpect(jsonPath("$[0].fechaEjecucion").value(hoy.toString()))
        .andExpect(
            jsonPath("$[*].cuadrilla.nombre", contains(norte.getNombre(), norte.getNombre())));

    for (OrdenTrabajo ot : List.of(ot1, ot2)) {
      OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
      assertThat(guardada.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
      assertThat(guardada.getIdCuadrilla()).isEqualTo(norte.getId());
      assertThat(tramos(ot)).containsExactly(tramoAbierto(norte));
    }
    assertThat(eventos.stream(OrdenAsignada.class))
        .extracting(OrdenAsignada::idOrdenTrabajo)
        .containsExactlyInAnyOrder(ot1.getId(), ot2.getId());
  }

  @Test
  void reasignarAOtraCuadrilla_cierraElTramoAnteriorYAbreOtro() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> o.asignar(norte.getId(), hoy, ahora));

    asignar(jefatura, List.of(ot.getId()), sur.getId(), hoy.plusDays(1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cuadrilla.id").value(sur.getId()))
        .andExpect(jsonPath("$[0].cuadrilla.nombre").value("Cuadrilla Asignar Sur"));

    List<Map<String, Object>> tramos = tramos(ot);
    assertThat(tramos).hasSize(2);
    assertThat(tramos.get(0)).containsEntry("id_cuadrilla", norte.getId());
    assertThat(tramos.get(0).get("hasta")).isNotNull();
    assertThat(tramos.get(1)).isEqualTo(tramoAbierto(sur));
    assertThat(eventos.stream(OrdenAsignada.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.idCuadrillaAnterior()).isEqualTo(norte.getId());
              assertThat(e.idCuadrillaNueva()).isEqualTo(sur.getId());
            });
    assertThat(eventos.stream(EstadoOrdenCambiado.class)).isEmpty();
  }

  @Test
  void unaEnProceso_400ConSuNumeroYNoAsignaNinguna() throws Exception {
    OrdenTrabajo pendiente = guardarOt(o -> {});
    OrdenTrabajo enProceso =
        guardarOt(
            o -> {
              o.asignar(sur.getId(), hoy, ahora);
              o.iniciar(ahora);
            });

    asignar(jefatura, List.of(pendiente.getId(), enProceso.getId()), norte.getId(), hoy)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.detail", startsWith(enProceso.getNumero() + ":")));

    OrdenTrabajo sinTocar = ordenTrabajoRepository.findById(pendiente.getId()).orElseThrow();
    assertThat(sinTocar.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(sinTocar.getIdCuadrilla()).isNull();
    assertThat(tramos(pendiente)).isEmpty();
    assertThat(eventos.stream(OrdenAsignada.class)).isEmpty();
  }

  // --- Datos del pedido ---

  @Test
  void fechaAnteriorAHoy_400FechaAnterior() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> {});

    asignar(jefatura, List.of(ot.getId()), norte.getId(), hoy.minusDays(1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/fecha-anterior"));
    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.PENDIENTE);
  }

  @Test
  void cuadrillaInactiva_400ReglaNegocio() throws Exception {
    Cuadrilla inactiva = guardarCuadrilla("Cuadrilla Asignar Inactiva");
    inactiva.desactivar(0);
    cuadrillaRepository.save(inactiva);
    OrdenTrabajo ot = guardarOt(o -> {});

    asignar(jefatura, List.of(ot.getId()), inactiva.getId(), hoy)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void otInexistente_404() throws Exception {
    asignar(jefatura, List.of(999999L), norte.getId(), hoy)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  @Test
  void sinOts_400Validacion() throws Exception {
    asignar(jefatura, List.of(), norte.getId(), hoy)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("idsOrdenTrabajo")));
  }

  // --- Carga por cuadrilla (3) ---

  @Test
  void cargaPorCuadrilla_cuentaLasAsignadasDeEsaFecha() throws Exception {
    guardarOt(o -> o.asignar(norte.getId(), hoy.plusDays(3), ahora));
    guardarOt(o -> o.asignar(norte.getId(), hoy.plusDays(3), ahora));
    guardarOt(o -> o.asignar(norte.getId(), hoy.plusDays(4), ahora));

    mvc.perform(
            get(ORDENES + "/carga-por-cuadrilla")
                .param("fecha", hoy.plusDays(3).toString())
                .with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.idCuadrilla == %d)].ordenes".formatted(norte.getId()), contains(2)));
  }

  @Test
  void cargaPorCuadrilla_sinFecha_400Validacion() throws Exception {
    mvc.perform(get(ORDENES + "/carga-por-cuadrilla").with(user(jefatura)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"));
  }

  // --- Permisos ---

  @Test
  void operadorYTecnico_403EnLosDosEndpoints() throws Exception {
    OrdenTrabajo ot = guardarOt(o -> {});

    for (UsuarioAutenticado quien : List.of(operador, tecnico)) {
      asignar(quien, List.of(ot.getId()), norte.getId(), hoy).andExpect(status().isForbidden());
      mvc.perform(
              get(ORDENES + "/carga-por-cuadrilla")
                  .param("fecha", hoy.toString())
                  .with(user(quien)))
          .andExpect(status().isForbidden());
    }
    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado())
        .isEqualTo(EstadoOT.PENDIENTE);
  }

  // --- Ayudas ---

  private ResultActions asignar(
      UsuarioAutenticado quien, List<Long> ids, Long idCuadrilla, LocalDate fecha)
      throws Exception {
    String listaIds = ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    return mvc.perform(
        post(ORDENES + "/asignar")
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
          {"idsOrdenTrabajo": [%s], "idCuadrilla": %d, "fechaEjecucion": "%s"}
          """
                    .formatted(listaIds, idCuadrilla, fecha)));
  }

  /** Los tramos de la OT leídos de la base, del más viejo al más nuevo. */
  private List<Map<String, Object>> tramos(OrdenTrabajo ot) {
    return jdbc.queryForList(
        "SELECT id_cuadrilla, hasta FROM tramo_cuadrilla_ot"
            + " WHERE id_orden_trabajo = ? ORDER BY desde",
        ot.getId());
  }

  /** Cómo se ve un tramo abierto de la cuadrilla en {@link #tramos}. */
  private static Map<String, Object> tramoAbierto(Cuadrilla cuadrilla) {
    Map<String, Object> tramo = new java.util.HashMap<>();
    tramo.put("id_cuadrilla", cuadrilla.getId());
    tramo.put("hasta", null);
    return tramo;
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
