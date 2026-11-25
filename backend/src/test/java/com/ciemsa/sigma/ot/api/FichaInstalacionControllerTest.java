package com.ciemsa.sigma.ot.api;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Ficha de la instalación (#92, HU-10) contra PostgreSQL. OTs de MTOP con las reglas de datos de
 * prueba (ALTA 8 h, MEDIA 48 h, BAJA 120 h) en la instalación TEST-FICHA-1, más una OT en curso en
 * TEST-FICHA-2 que no tiene que aparecer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FichaInstalacionControllerTest {

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
  private final List<Long> instalaciones = new ArrayList<>();
  private Instant ahora;
  private Long mtop;
  private Long idCategoria;
  private Long idInstalacion;
  private Long idOtraInstalacion;
  private Cuadrilla norte;
  private Cuadrilla sur;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnicoNorte;

  private OrdenTrabajo vencida;
  private OrdenTrabajo media;
  private OrdenTrabajo deNorte;
  private OrdenTrabajo deSur;
  private OrdenTrabajo sinSla;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    norte = guardarCuadrilla("Cuadrilla Ficha Norte");
    sur = guardarCuadrilla("Cuadrilla Ficha Sur");
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.ficha@ciemsa.com", Rol.JEFATURA, null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.ficha@ciemsa.com", Rol.OPERADOR, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico", "tecnico.ficha@ciemsa.com", Rol.TECNICO, norte));
    mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion = guardarInstalacion("TEST-FICHA-1", "Cruce de la ficha");
    idOtraInstalacion = guardarInstalacion("TEST-FICHA-2", "Cruce vecino");

    // En curso: vencida (hace 2 h), media (vence en 47 h), de Norte (en proceso, 119 h), de Sur
    // (en pausa, 115 h) y sin SLA.
    vencida = guardarCorrectiva(idInstalacion, Prioridad.ALTA, 10, true, ot -> {});
    media = guardarCorrectiva(idInstalacion, Prioridad.MEDIA, 1, true, ot -> {});
    deNorte =
        guardarCorrectiva(
            idInstalacion,
            Prioridad.BAJA,
            1,
            true,
            ot -> {
              ot.asignar(norte.getId(), LocalDate.now(), ahora);
              ot.iniciar(ahora);
            });
    deSur =
        guardarCorrectiva(
            idInstalacion,
            Prioridad.BAJA,
            5,
            true,
            ot -> {
              ot.asignar(sur.getId(), LocalDate.now(), ahora);
              ot.pausar(MotivoPausa.CLIMA, null, operador.getId(), ahora);
            });
    sinSla = guardarCorrectiva(idInstalacion, Prioridad.MEDIA, 1, false, ot -> {});

    // Fuera de la ficha: cerrada técnicamente, anulada y la de otra instalación.
    guardarCorrectiva(
        idInstalacion,
        Prioridad.MEDIA,
        1,
        true,
        ot -> {
          ot.asignar(norte.getId(), LocalDate.now(), ahora);
          ot.iniciar(ahora);
          ot.cerrarTecnicamente(
              new CierreTecnico("Diagnóstico", "Solución", null), UUID.randomUUID(), ahora);
        });
    guardarCorrectiva(
        idInstalacion,
        Prioridad.BAJA,
        1,
        true,
        ot -> ot.anular("Duplicada", jefatura.getId(), ahora));
    guardarCorrectiva(idOtraInstalacion, Prioridad.ALTA, 1, true, ot -> {});
  }

  @AfterEach
  void tearDown() {
    for (Long id : instalaciones) {
      String ordenes = " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)";
      jdbc.update("DELETE FROM pausa_ot WHERE id_orden_trabajo IN" + ordenes, id);
      jdbc.update("DELETE FROM tramo_cuadrilla_ot WHERE id_orden_trabajo IN" + ordenes, id);
      jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", id);
      jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", id);
      jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", id);
    }
    usuarios.forEach(usuarioRepository::deleteById);
    cuadrillas.forEach(cuadrillaRepository::deleteById);
  }

  // --- Datos ---

  @Test
  void obtener_jefatura_devuelveDatosYOrganismoConLoQueFaltaEnNull() throws Exception {
    ficha(jefatura)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(idInstalacion))
        .andExpect(jsonPath("$.codigo").value("TEST-FICHA-1"))
        .andExpect(jsonPath("$.nombre").value("Cruce de la ficha"))
        .andExpect(jsonPath("$.rutaZona").value("Ruta 1"))
        .andExpect(jsonPath("$.latitud").value(-34.78))
        .andExpect(jsonPath("$.longitud").value(-56.25))
        .andExpect(jsonPath("$.tipo").doesNotExist())
        .andExpect(jsonPath("$.direccion").doesNotExist())
        .andExpect(jsonPath("$.activa").value(true))
        .andExpect(jsonPath("$.organismo.id").value(mtop))
        .andExpect(jsonPath("$.organismo.nombre").value("MTOP"));
  }

  @Test
  void obtener_inactiva_igualSeConsulta() throws Exception {
    jdbc.update("UPDATE instalacion SET activa = false WHERE id_instalacion = ?", idInstalacion);

    ficha(operador).andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));
  }

  // --- OTs en curso ---

  @Test
  void obtener_soloLasEnCursoDeLaInstalacion_primeroLaQueVenceAntesYSinSlaAlFinal()
      throws Exception {
    ficha(jefatura)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.ordenesEnCurso[*].numero",
                contains(
                    vencida.getNumero(),
                    media.getNumero(),
                    deSur.getNumero(),
                    deNorte.getNumero(),
                    sinSla.getNumero())));
  }

  @Test
  void obtener_cadaOt_traeEstadoSlaCuadrillaEIncidente() throws Exception {
    ficha(jefatura)
        .andExpect(jsonPath(campo(vencida, "vencida"), contains(true)))
        .andExpect(jsonPath(campo(vencida, "slaDefinido"), contains(true)))
        .andExpect(jsonPath(campo(vencida, "prioridad"), contains("ALTA")))
        .andExpect(jsonPath(campo(vencida, "estado"), contains("PENDIENTE")))
        .andExpect(jsonPath(campo(vencida, "tipo"), contains("CORRECTIVA")))
        .andExpect(
            jsonPath(
                campo(vencida, "numeroIncidente"), contains(vencida.getIncidente().getNumero())))
        .andExpect(jsonPath(campo(media, "vencida"), contains(false)))
        .andExpect(jsonPath(campo(sinSla, "slaDefinido"), contains(false)))
        .andExpect(jsonPath(campo(deNorte, "estado"), contains("EN_PROCESO")))
        .andExpect(jsonPath(campo(deNorte, "cuadrilla.nombre"), contains("Cuadrilla Ficha Norte")))
        .andExpect(jsonPath(campo(deSur, "estado"), contains("EN_PAUSA")))
        .andExpect(jsonPath(campo(media, "puedeVerDetalle"), contains(true)));
  }

  @Test
  void obtener_tecnico_puedeVerDetalleSoloEnLasDeSuCuadrilla() throws Exception {
    ficha(tecnicoNorte)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordenesEnCurso.length()").value(5))
        .andExpect(jsonPath(campo(deNorte, "puedeVerDetalle"), contains(true)))
        .andExpect(jsonPath(campo(deSur, "puedeVerDetalle"), contains(false)))
        .andExpect(jsonPath(campo(media, "puedeVerDetalle"), contains(false)));
  }

  @Test
  void obtener_otraInstalacion_traeLasSuyasYSinOtsListaVacia() throws Exception {
    mvc.perform(get(url(idOtraInstalacion)).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordenesEnCurso.length()").value(1));
    Long sinOts = guardarInstalacion("TEST-FICHA-3", "Cruce sin OTs");

    mvc.perform(get(url(sinOts)).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordenesEnCurso").isEmpty());
  }

  // --- Errores ---

  @Test
  void obtener_inexistente_devuelve404() throws Exception {
    mvc.perform(get(url(999_999L)).with(user(jefatura)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  @Test
  void obtener_sinSesion_devuelve401() throws Exception {
    mvc.perform(get(url(idInstalacion)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
  }

  private ResultActions ficha(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(get(url(idInstalacion)).with(user(quien)));
  }

  private static String url(Long id) {
    return "/api/v1/instalaciones/" + id + "/ficha";
  }

  /** JsonPath de un campo de esa OT en la ficha: devuelve una lista de un elemento. */
  private static String campo(OrdenTrabajo ot, String campo) {
    return "$.ordenesEnCurso[?(@.numero == '" + ot.getNumero() + "')]." + campo;
  }

  private Long guardarInstalacion(String codigo, String nombre) {
    Long id =
        instalacionRepository
            .save(
                Instalacion.crear(
                    codigo,
                    nombre,
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
    instalaciones.add(id);
    return id;
  }

  private Cuadrilla guardarCuadrilla(String nombre) {
    Cuadrilla cuadrilla = cuadrillaRepository.save(Cuadrilla.crear(nombre, null));
    cuadrillas.add(cuadrilla.getId());
    return cuadrilla;
  }

  /** Correctiva de MTOP generada hace {@code horasAtras} horas y llevada a su estado. */
  private OrdenTrabajo guardarCorrectiva(
      Long instalacion,
      Prioridad prioridad,
      int horasAtras,
      boolean conSla,
      Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente =
        incidenteRepository.save(
            Incidente.registrar(
                instalacion,
                OrigenIncidente.MONITOREO,
                idCategoria,
                prioridad,
                "Falla de prueba",
                operador.getId()));
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
