package com.ciemsa.sigma.pendientes;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
 * Bandeja «Mis OTs» (#88, HU-07) contra PostgreSQL: las OTs se arman con el dominio y se guardan
 * por los repositorios; anular y reasignar pasan por los endpoints de la Jefatura (#47). También
 * las pendientes sin asignar que el técnico puede tomar (#93, HU-20).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MisOtsControllerTest {

  private static final String MIS_OTS = "/api/v1/mis-ots";
  private static final String ORDENES = "/api/v1/ordenes";
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
  private UsuarioAutenticado operador;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado tecnicoNorte;
  private UsuarioAutenticado tecnicoSur;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    hoy = LocalDate.now();
    norte = guardarCuadrilla("Cuadrilla Bandeja Norte");
    sur = guardarCuadrilla("Cuadrilla Bandeja Sur");
    operador =
        enSesion(guardarUsuario("Operador", "operador.bandeja@ciemsa.com", Rol.OPERADOR, null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.bandeja@ciemsa.com", Rol.JEFATURA, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico Norte", "norte.bandeja@ciemsa.com", Rol.TECNICO, norte));
    tecnicoSur =
        enSesion(guardarUsuario("Técnico Sur", "sur.bandeja@ciemsa.com", Rol.TECNICO, sur));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-BANDEJA-1",
                    "Cruce de la bandeja",
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

  @Test
  void misOts_soloLasDeSuCuadrillaEnAsignadaEnProcesoYEnPausa() throws Exception {
    OrdenTrabajo asignada = guardarOt(Prioridad.MEDIA, norte, hoy, ot -> {});
    OrdenTrabajo enProceso = guardarOt(Prioridad.MEDIA, norte, hoy, this::iniciar);
    OrdenTrabajo enPausa =
        guardarOt(
            Prioridad.MEDIA,
            norte,
            hoy,
            ot -> {
              iniciar(ot);
              ot.pausar(MotivoPausa.TERCERO, "UTE", tecnicoNorte.getId(), Instant.now());
            });
    // Fuera de la bandeja: cerrada, de otra cuadrilla y sin asignar
    guardarOt(
        Prioridad.MEDIA,
        norte,
        hoy,
        ot -> {
          iniciar(ot);
          ot.cerrarTecnicamente(
              new CierreTecnico("Diagnóstico", "Solución", null), UUID.randomUUID(), Instant.now());
        });
    guardarOt(Prioridad.MEDIA, sur, hoy, ot -> {});
    guardarOt(Prioridad.MEDIA, null, null, ot -> {});

    misOts(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(3))
        .andExpect(
            jsonPath("$.contenido[*].id")
                .value(
                    containsInAnyOrder(
                        asignada.getId().intValue(),
                        enProceso.getId().intValue(),
                        enPausa.getId().intValue())));
  }

  @Test
  void misOts_traeInstalacionPrioridadVencimientoYPausa() throws Exception {
    OrdenTrabajo enPausa =
        guardarOt(
            Prioridad.MEDIA,
            norte,
            hoy,
            ot -> {
              iniciar(ot);
              ot.pausar(MotivoPausa.TERCERO, "UTE", tecnicoNorte.getId(), Instant.now());
            });
    jdbc.update(
        "UPDATE instalacion SET direccion = 'Ruta 9 km 100' WHERE id_instalacion = ?",
        idInstalacion);

    misOts(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido", hasSize(1)))
        .andExpect(jsonPath("$.contenido[0].id").value(enPausa.getId()))
        .andExpect(jsonPath("$.contenido[0].numero").value(enPausa.getNumero()))
        .andExpect(jsonPath("$.contenido[0].estado").value("EN_PAUSA"))
        .andExpect(jsonPath("$.contenido[0].estadoAnteriorPausa").value("EN_PROCESO"))
        .andExpect(jsonPath("$.contenido[0].prioridad").value("MEDIA"))
        .andExpect(jsonPath("$.contenido[0].categoria").value("Lámpara quemada"))
        .andExpect(jsonPath("$.contenido[0].numeroIncidente").isNotEmpty())
        .andExpect(jsonPath("$.contenido[0].fechaEjecucion").value(hoy.toString()))
        .andExpect(jsonPath("$.contenido[0].fechaLimite").isNotEmpty())
        .andExpect(jsonPath("$.contenido[0].slaDefinido").value(true))
        .andExpect(jsonPath("$.contenido[0].vencida").value(false))
        .andExpect(jsonPath("$.contenido[0].iniciadaEn").isNotEmpty())
        .andExpect(jsonPath("$.contenido[0].instalacion.codigo").value("TEST-BANDEJA-1"))
        .andExpect(jsonPath("$.contenido[0].instalacion.nombre").value("Cruce de la bandeja"))
        .andExpect(jsonPath("$.contenido[0].instalacion.direccion").value("Ruta 9 km 100"))
        .andExpect(jsonPath("$.contenido[0].instalacion.rutaZona").value("Ruta 9"))
        .andExpect(jsonPath("$.contenido[0].instalacion.latitud").value(-34.8))
        .andExpect(jsonPath("$.contenido[0].instalacion.longitud").value(-55.9))
        .andExpect(jsonPath("$.contenido[0].pausa.motivo").value("TERCERO"))
        .andExpect(jsonPath("$.contenido[0].pausa.detalle").value("UTE"));
  }

  @Test
  void misOts_porFechaDeEjecucionYDespuesPorVencimiento() throws Exception {
    OrdenTrabajo manana = guardarOt(Prioridad.ALTA, norte, hoy.plusDays(1), ot -> {});
    OrdenTrabajo hoyMedia = guardarOt(Prioridad.MEDIA, norte, hoy, ot -> {});
    OrdenTrabajo hoyAlta = guardarOt(Prioridad.ALTA, norte, hoy, ot -> {});
    OrdenTrabajo atrasada = guardarOt(Prioridad.BAJA, norte, hoy.minusDays(1), ot -> {});

    misOts(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.contenido[*].id")
                .value(
                    contains(
                        atrasada.getId().intValue(),
                        hoyAlta.getId().intValue(),
                        hoyMedia.getId().intValue(),
                        manana.getId().intValue())))
        .andExpect(jsonPath("$.contenido[0].iniciadaEn").value(nullValue()))
        .andExpect(jsonPath("$.contenido[0].pausa").value(nullValue()));
  }

  @Test
  void misOts_paginada() throws Exception {
    for (int i = 0; i < 3; i++) {
      guardarOt(Prioridad.MEDIA, norte, hoy, ot -> {});
    }

    mvc.perform(get(MIS_OTS).param("size", "2").with(user(tecnicoNorte)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido", hasSize(2)))
        .andExpect(jsonPath("$.totalElementos").value(3))
        .andExpect(jsonPath("$.totalPaginas").value(2));
  }

  @Test
  void misOts_cuadrillaLeidaDeLaBaseYNoDeLaSesion() throws Exception {
    OrdenTrabajo deSur = guardarOt(Prioridad.MEDIA, sur, hoy, ot -> {});
    // La sesión dice Norte, pero en la base ya está en Sur
    jdbc.update(
        "UPDATE usuario SET id_cuadrilla = ? WHERE id_usuario = ?",
        sur.getId(),
        tecnicoNorte.getId());

    misOts(tecnicoNorte)
        .andExpect(jsonPath("$.contenido[*].id").value(contains(deSur.getId().intValue())));
  }

  // --- Pendiente de #47 ---

  @Test
  void otAsignadaQueSeAnula_saleDeLaBandeja() throws Exception {
    OrdenTrabajo ot = guardarOt(Prioridad.MEDIA, norte, hoy, o -> {});
    misOts(tecnicoNorte).andExpect(jsonPath("$.totalElementos").value(1));

    mvc.perform(
            post(ORDENES + "/" + ot.getId() + "/anular")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Duplicada\"}"))
        .andExpect(status().isOk());

    misOts(tecnicoNorte).andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void otAsignadaQueSeReasigna_pasaALaBandejaDeLaOtraCuadrilla() throws Exception {
    OrdenTrabajo ot = guardarOt(Prioridad.MEDIA, norte, hoy, o -> {});

    mvc.perform(
            post(ORDENES + "/asignar")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"idsOrdenTrabajo\": [%d], \"idCuadrilla\": %d, \"fechaEjecucion\": \"%s\"}"
                        .formatted(ot.getId(), sur.getId(), hoy)))
        .andExpect(status().isOk());

    misOts(tecnicoNorte).andExpect(jsonPath("$.totalElementos").value(0));
    misOts(tecnicoSur)
        .andExpect(jsonPath("$.contenido[*].id").value(contains(ot.getId().intValue())));
  }

  // --- Pendientes sin asignar (HU-20) ---

  @Test
  void pendientesSinAsignar_soloLasPendientesSinCuadrilla() throws Exception {
    OrdenTrabajo sinAsignar = guardarOt(Prioridad.MEDIA, null, null, ot -> {});
    OrdenTrabajo asignada = guardarOt(Prioridad.MEDIA, sur, hoy, ot -> {});
    OrdenTrabajo enPausa =
        guardarOt(
            Prioridad.MEDIA,
            null,
            null,
            ot -> ot.pausar(MotivoPausa.CLIMA, null, operador.getId(), Instant.now()));

    pendientesSinAsignar(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[*].id").value(hasItem(sinAsignar.getId().intValue())))
        .andExpect(jsonPath("$.contenido[*].id").value(not(hasItem(asignada.getId().intValue()))))
        .andExpect(jsonPath("$.contenido[*].id").value(not(hasItem(enPausa.getId().intValue()))));
  }

  @Test
  void pendientesSinAsignar_laMasUrgentePrimero() throws Exception {
    OrdenTrabajo media = guardarOt(Prioridad.MEDIA, null, null, ot -> {});
    OrdenTrabajo alta = guardarOt(Prioridad.ALTA, null, null, ot -> {});

    pendientesSinAsignar(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.contenido[?(@.id in [%d, %d])].id".formatted(alta.getId(), media.getId()))
                .value(contains(alta.getId().intValue(), media.getId().intValue())));
  }

  @Test
  void pendientesSinAsignar_operadorYJefatura_devuelven403() throws Exception {
    mvc.perform(get(MIS_OTS + "/pendientes-sin-asignar").with(user(operador)))
        .andExpect(status().isForbidden());
    mvc.perform(get(MIS_OTS + "/pendientes-sin-asignar").with(user(jefatura)))
        .andExpect(status().isForbidden());
  }

  // --- Permisos ---

  @Test
  void operadorYJefatura_devuelven403() throws Exception {
    misOts(operador)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
    misOts(jefatura).andExpect(status().isForbidden());
  }

  @Test
  void sinSesion_devuelve401() throws Exception {
    mvc.perform(get(MIS_OTS)).andExpect(status().isUnauthorized());
  }

  // --- Auxiliares ---

  private ResultActions misOts(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(get(MIS_OTS).with(user(quien)));
  }

  /** De a 100 (el máximo): los datos de prueba también tienen pendientes sin asignar. */
  private ResultActions pendientesSinAsignar(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(
        get(MIS_OTS + "/pendientes-sin-asignar").param("size", "100").with(user(quien)));
  }

  private void iniciar(OrdenTrabajo ot) {
    ot.iniciar(Instant.now());
  }

  /**
   * OT correctiva de MTOP con la prioridad pedida, generada hace una hora; asignada a {@code
   * cuadrilla} si no es null y llevada a su estado por {@code preparar}.
   */
  private OrdenTrabajo guardarOt(
      Prioridad prioridad,
      Cuadrilla cuadrilla,
      LocalDate fechaEjecucion,
      Consumer<OrdenTrabajo> preparar) {
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
                prioridad,
                "Lámpara roja quemada",
                operador.getId()));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    ReglaSLA regla =
        reglaSlaRepository.findByIdOrganismoAndPrioridad(mtop, prioridad).orElseThrow();
    OrdenTrabajo nueva = incidente.generarOrdenCorrectiva(hace1h, regla);
    if (cuadrilla != null) {
      nueva.asignar(cuadrilla.getId(), fechaEjecucion, hace1h);
    }
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
