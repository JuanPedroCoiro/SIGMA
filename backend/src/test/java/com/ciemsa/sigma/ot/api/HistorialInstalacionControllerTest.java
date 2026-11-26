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
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Historial de la instalación (#92, HU-10) contra PostgreSQL, en TEST-HIST-1. Las fechas de la
 * línea de tiempo se fijan para que el orden no dependa de cuándo se guardó cada fila: anulada hace
 * 10 min, incidente cerrado sin OT hace 30 min, incidente en espera hace 1 h, validada hace 2 h y
 * cerrada técnicamente hace 3 h.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HistorialInstalacionControllerTest {

  private static final String HASH = "$2a$10$hashDePrueba";

  @Autowired private WebApplicationContext context;
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
  private final List<Long> instalaciones = new ArrayList<>();
  private Instant ahora;
  private Long mtop;
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla norte;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnicoSur;

  private OrdenTrabajo cerrada;
  private OrdenTrabajo validada;
  private OrdenTrabajo anulada;
  private Incidente enEspera;
  private Incidente cerradoSinOt;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    norte = guardarCuadrilla("Cuadrilla Historial Norte");
    Cuadrilla sur = guardarCuadrilla("Cuadrilla Historial Sur");
    jefatura = enSesion(guardarUsuario("Jefatura", "jefatura.hist@ciemsa.com", Rol.JEFATURA, null));
    operador = enSesion(guardarUsuario("Operador", "operador.hist@ciemsa.com", Rol.OPERADOR, null));
    tecnicoSur =
        enSesion(guardarUsuario("Técnico Sur", "tecnico.hist@ciemsa.com", Rol.TECNICO, sur));
    mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion = guardarInstalacion("TEST-HIST-1");
    Long idOtra = guardarInstalacion("TEST-HIST-2");

    cerrada = guardarCorrectiva(idInstalacion, ot -> cerrar(ot, horasAtras(3), "Cable cortado"));
    validada =
        guardarCorrectiva(
            idInstalacion,
            ot -> {
              cerrar(ot, horasAtras(2), "Lámpara quemada");
              ot.validar(horasAtras(2));
            });
    anulada =
        guardarCorrectiva(idInstalacion, ot -> ot.anular("Duplicada", jefatura.getId(), ahora));
    fijar("orden_trabajo", "id_orden_trabajo", anulada.getId(), "modificado_en", minutosAtras(10));

    enEspera = registrarIncidente(idInstalacion);
    enEspera.ponerEnEsperaDeTercero("UTE", "Corte de energía");
    enEspera = incidenteRepository.save(enEspera);
    fijar("incidente", "id_incidente", enEspera.getId(), "creado_en", horasAtras(1));
    cerradoSinOt = registrarIncidente(idInstalacion);
    cerradoSinOt.ponerEnEsperaDeTercero("Antel", null);
    cerradoSinOt.cerrarSinOt();
    cerradoSinOt = incidenteRepository.save(cerradoSinOt);
    fijar("incidente", "id_incidente", cerradoSinOt.getId(), "creado_en", minutosAtras(30));

    // Fuera del historial: una en curso (su incidente queda CON_OT) y una cerrada de otra
    // instalación.
    guardarCorrectiva(idInstalacion, ot -> ot.asignar(norte.getId(), LocalDate.now(), ahora));
    guardarCorrectiva(idOtra, ot -> cerrar(ot, horasAtras(1), "De otro cruce"));
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

  // --- Qué entra y en qué orden ---

  @Test
  void listar_otsTerminadasEIncidentesSinOt_deLoMasNuevoALoMasViejo() throws Exception {
    historial(jefatura)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(5))
        .andExpect(
            jsonPath(
                "$.contenido[*].numero",
                contains(
                    anulada.getNumero(),
                    cerradoSinOt.getNumero(),
                    enEspera.getNumero(),
                    validada.getNumero(),
                    cerrada.getNumero())))
        .andExpect(
            jsonPath("$.contenido[*].clase", contains("OT", "INCIDENTE", "INCIDENTE", "OT", "OT")));
  }

  @Test
  void listar_cadaEntrada_traeSuEstadoYSusDatos() throws Exception {
    historial(jefatura)
        .andExpect(jsonPath(campo(cerrada, "estadoOt"), contains("CERRADA_TECNICA")))
        .andExpect(jsonPath(campo(cerrada, "diagnostico"), contains("Cable cortado")))
        .andExpect(jsonPath(campo(cerrada, "solucion"), contains("Se reemplazó")))
        .andExpect(jsonPath(campo(cerrada, "cuadrilla.nombre"), contains(norte.getNombre())))
        .andExpect(jsonPath(campo(cerrada, "tipoOt"), contains("CORRECTIVA")))
        .andExpect(jsonPath(campo(cerrada, "incidente.categoria"), contains("Lámpara quemada")))
        .andExpect(jsonPath(campo(cerrada, "incidente.prioridad"), contains("MEDIA")))
        .andExpect(jsonPath(campo(validada, "estadoOt"), contains("VALIDADA")))
        .andExpect(jsonPath(campo(anulada, "estadoOt"), contains("ANULADA")))
        .andExpect(jsonPath(campo(anulada, "motivoAnulacion"), contains("Duplicada")))
        .andExpect(jsonPath(campo(enEspera, "estadoIncidente"), contains("EN_ESPERA_TERCERO")))
        .andExpect(jsonPath(campo(enEspera, "tercero"), contains("UTE")))
        .andExpect(jsonPath(campo(enEspera, "causa"), contains("Corte de energía")))
        .andExpect(jsonPath(campo(cerradoSinOt, "estadoIncidente"), contains("CERRADO_SIN_OT")))
        .andExpect(jsonPath(campo(cerradoSinOt, "estadoOt"), contains((Object) null)));
  }

  @Test
  void listar_fechaDeLaLineaDeTiempo_esElCierreLaAnulacionOElReporte() throws Exception {
    historial(jefatura)
        .andExpect(jsonPath(campo(cerrada, "fecha"), contains(horasAtras(3).toString())))
        .andExpect(jsonPath(campo(anulada, "fecha"), contains(minutosAtras(10).toString())))
        .andExpect(jsonPath(campo(enEspera, "fecha"), contains(horasAtras(1).toString())));
  }

  @Test
  void listar_paginado_deADos() throws Exception {
    mvc.perform(url(idInstalacion).param("size", "2").param("page", "1").with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pagina").value(1))
        .andExpect(jsonPath("$.totalElementos").value(5))
        .andExpect(jsonPath("$.totalPaginas").value(3))
        .andExpect(
            jsonPath(
                "$.contenido[*].numero", contains(enEspera.getNumero(), validada.getNumero())));
  }

  @Test
  void listar_sinIntervenciones_contenidoVacio() throws Exception {
    Long sinHistoria = guardarInstalacion("TEST-HIST-3");

    mvc.perform(url(sinHistoria).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isEmpty())
        .andExpect(jsonPath("$.totalElementos").value(0));
  }

  // --- Quién ve qué ---

  @Test
  void listar_tecnicoDeOtraCuadrilla_puedeVerElDetalleDeLasOtsTerminadas() throws Exception {
    historial(tecnicoSur)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.contenido[*].puedeVerDetalle", contains(true, false, false, true, true)));
  }

  @Test
  void listar_inexistente_devuelve404() throws Exception {
    mvc.perform(url(999_999L).with(user(jefatura)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  @Test
  void listar_sinSesion_devuelve401() throws Exception {
    mvc.perform(url(idInstalacion)).andExpect(status().isUnauthorized());
  }

  private ResultActions historial(UsuarioAutenticado quien) throws Exception {
    return mvc.perform(url(idInstalacion).with(user(quien)));
  }

  private static MockHttpServletRequestBuilder url(Long id) {
    return get("/api/v1/instalaciones/" + id + "/historial");
  }

  private static String campo(OrdenTrabajo ot, String campo) {
    return "$.contenido[?(@.numero == '" + ot.getNumero() + "')]." + campo;
  }

  private static String campo(Incidente incidente, String campo) {
    return "$.contenido[?(@.numero == '" + incidente.getNumero() + "')]." + campo;
  }

  private Instant horasAtras(int horas) {
    return ahora.minus(Duration.ofHours(horas));
  }

  private Instant minutosAtras(int minutos) {
    return ahora.minus(Duration.ofMinutes(minutos));
  }

  private void fijar(String tabla, String pk, Long id, String columna, Instant valor) {
    jdbc.update(
        "UPDATE " + tabla + " SET " + columna + " = ? WHERE " + pk + " = ?",
        Timestamp.from(valor),
        id);
  }

  private void cerrar(OrdenTrabajo ot, Instant cuando, String diagnostico) {
    ot.asignar(norte.getId(), LocalDate.now(), cuando);
    ot.iniciar(cuando);
    ot.cerrarTecnicamente(
        new CierreTecnico(diagnostico, "Se reemplazó", null), UUID.randomUUID(), cuando);
  }

  private Long guardarInstalacion(String codigo) {
    Long id =
        instalacionRepository
            .save(
                Instalacion.crear(
                    codigo,
                    "Cruce del historial",
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

  private Incidente registrarIncidente(Long instalacion) {
    return incidenteRepository.save(
        Incidente.registrar(
            instalacion,
            OrigenIncidente.MONITOREO,
            idCategoria,
            Prioridad.MEDIA,
            "Falla de prueba",
            operador.getId()));
  }

  /** Correctiva sin SLA generada hace 4 h y llevada a su estado por {@code ajuste}. */
  private OrdenTrabajo guardarCorrectiva(Long instalacion, Consumer<OrdenTrabajo> ajuste) {
    Incidente incidente = registrarIncidente(instalacion);
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(horasAtras(4), null);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    return ordenTrabajoRepository.save(ot);
  }

  private Usuario guardarUsuario(String nombre, String correo, Rol rol, Cuadrilla cuadrilla) {
    Usuario usuario = usuarioRepository.save(Usuario.crear(nombre, correo, HASH, rol, cuadrilla));
    usuarios.add(usuario.getId());
    return usuario;
  }

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
