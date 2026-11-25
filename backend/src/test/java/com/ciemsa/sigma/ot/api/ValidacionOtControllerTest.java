package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
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
import com.ciemsa.sigma.ot.domain.Adjunto;
import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.ot.domain.TipoImagen;
import com.ciemsa.sigma.ot.infrastructure.AdjuntoRepository;
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
 * Validar u observar una OT cerrada (#91, HU-09, CU-5) contra PostgreSQL. Las OTs son correctivas
 * MEDIA de MTOP, asignadas a Norte y cerradas técnicamente con el dominio; las fotos del cierre se
 * guardan por el repositorio (validar solo mira sus datos, no los archivos).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ValidacionOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String POR_VALIDAR = "/api/v1/pendientes/por-validar";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final CierreTecnico CIERRE =
      new CierreTecnico("Lámpara roja quemada", "Cambio de lámpara", null);

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private ReglaSlaRepository reglaSlaRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;
  @Autowired private AdjuntoRepository adjuntoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private final List<Long> cuadrillas = new ArrayList<>();
  private Long idInstalacion;

  private Cuadrilla norte;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado tecnicoNorte;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    norte = guardarCuadrilla("Cuadrilla Validación Norte");
    operador =
        enSesion(guardarUsuario("Operador", "operador.validacion@ciemsa.com", Rol.OPERADOR, null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.validacion@ciemsa.com", Rol.JEFATURA, null));
    tecnicoNorte =
        enSesion(
            guardarUsuario("Técnico Norte", "norte.validacion@ciemsa.com", Rol.TECNICO, norte));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-VALID-1",
                    "Cruce de validación",
                    "Ruta 5",
                    new BigDecimal("-34.700000"),
                    new BigDecimal("-56.200000"),
                    mtop))
            .getId();
  }

  @AfterEach
  void tearDown() {
    String ordenesDeLaInstalacion =
        " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)";
    jdbc.update(
        "DELETE FROM adjunto WHERE id_orden_trabajo IN" + ordenesDeLaInstalacion, idInstalacion);
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

  // --- Validar (paso 5) ---

  @Test
  void validar_conAntesYDespues_quedaValidadaYEnLaBitacora() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    guardarFotos(ot, MomentoFoto.ANTES, MomentoFoto.DESPUES);

    validar(jefatura, ot, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("VALIDADA"));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getEstado()).isEqualTo(EstadoOT.VALIDADA);
    assertThat(guardada.getValidadaEn()).isNotNull();
    assertThat(bitacora(ot, "CAMBIO_ESTADO"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("id_usuario")).isEqualTo(jefatura.getId());
              assertThat(r.get("estado_nuevo")).isEqualTo("VALIDADA");
            });
  }

  @Test
  void validar_sinFotoDelDespues_409ConLosMomentosYNoCambia() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    guardarFotos(ot, MomentoFoto.ANTES, MomentoFoto.DURANTE);

    validar(jefatura, ot, "{\"aceptarSinFotos\": false}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errores/fotos-faltantes"))
        .andExpect(jsonPath("$.momentosFaltantes").value(contains("DESPUES")));

    assertThat(estadoEnLaBase(ot)).isEqualTo(EstadoOT.CERRADA_TECNICA);
  }

  @Test
  void validar_sinFotosSinCuerpo_409ConAntesYDespues() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));

    validar(jefatura, ot, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.momentosFaltantes").value(contains("ANTES", "DESPUES")));
  }

  @Test
  void validar_sinFotosConfirmado_quedaValidada() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));

    validar(jefatura, ot, "{\"aceptarSinFotos\": true}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("VALIDADA"));
  }

  @Test
  void validar_yaValidada_400ConElEstadoActual() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    validar(jefatura, ot, "{\"aceptarSinFotos\": true}").andExpect(status().isOk());

    validar(jefatura, ot, "{\"aceptarSinFotos\": true}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("VALIDADA"));
  }

  @Test
  void validar_inexistente_404() throws Exception {
    mvc.perform(post(ORDENES + "/999999/validar").with(user(jefatura)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  // --- Observar (4a) ---

  @Test
  void observar_conMotivo_vuelveAAsignadaALaMismaCuadrillaYEnLaBitacora() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));

    observar(jefatura, ot, "  Falta la foto del después  ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ASIGNADA"))
        .andExpect(jsonPath("$.cuadrilla.id").value(norte.getId()));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getMotivoObservacion()).isEqualTo("Falta la foto del después");
    assertThat(guardada.getObservadaEn()).isNotNull();
    assertThat(bitacora(ot, "OBSERVACION"))
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.get("id_usuario")).isEqualTo(jefatura.getId());
              assertThat(r.get("motivo")).isEqualTo("Falta la foto del después");
            });
  }

  @Test
  void observarYCerrarDeNuevoSinLaFotoPedida_validarVuelveAAdvertir() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    guardarFotos(ot, MomentoFoto.ANTES, MomentoFoto.DESPUES);
    observar(jefatura, ot, "La foto del después no se ve").andExpect(status().isOk());
    // La cuadrilla vuelve a cerrar, pero solo con una foto del antes
    OrdenTrabajo observada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    observada.iniciar(Instant.now());
    observada.cerrarTecnicamente(CIERRE, UUID.randomUUID(), Instant.now());
    ordenTrabajoRepository.save(observada);
    guardarFotos(observada, MomentoFoto.ANTES);

    validar(jefatura, ot, null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.momentosFaltantes").value(contains("DESPUES")));
  }

  @Test
  void observar_sinMotivo_400YNoCambia() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));

    observar(jefatura, ot, "   ").andExpect(status().isBadRequest());

    assertThat(estadoEnLaBase(ot)).isEqualTo(EstadoOT.CERRADA_TECNICA);
  }

  @Test
  void observar_enProceso_400TransicionInvalida() throws Exception {
    OrdenTrabajo ot = guardarEnProceso();

    observar(jefatura, ot, "No corresponde")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.estadoActual").value("EN_PROCESO"));
  }

  // --- Por validar (paso 1) ---

  @Test
  void porValidar_soloLasCerradasDeLaMasViejaALaMasNueva() throws Exception {
    OrdenTrabajo nueva = guardarCerrada(Duration.ofHours(1));
    OrdenTrabajo vieja = guardarCerrada(Duration.ofHours(3));
    OrdenTrabajo enProceso = guardarEnProceso();

    mvc.perform(get(POR_VALIDAR).param("size", "100").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.contenido[?(@.id in [%d, %d])].id".formatted(vieja.getId(), nueva.getId()))
                .value(contains(vieja.getId().intValue(), nueva.getId().intValue())))
        .andExpect(jsonPath("$.contenido[*].id").value(not(hasItem(enProceso.getId().intValue()))));
  }

  @Test
  void porValidar_traeInstalacionCuadrillaYCumplimiento() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    String fila = "$.contenido[?(@.id == %d)]".formatted(ot.getId());

    mvc.perform(get(POR_VALIDAR).param("size", "100").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath(fila + ".numero").value(contains(ot.getNumero())))
        .andExpect(jsonPath(fila + ".prioridad").value(contains("MEDIA")))
        .andExpect(jsonPath(fila + ".codigoInstalacion").value(contains("TEST-VALID-1")))
        .andExpect(
            jsonPath(fila + ".nombreCuadrilla").value(contains("Cuadrilla Validación Norte")))
        .andExpect(jsonPath(fila + ".cerradaEnPlazo").value(contains(true)))
        .andExpect(jsonPath(fila + ".yaObservada").value(contains(false)));
  }

  // --- Detalle de la OT ---

  @Test
  void detalle_cerradaSinFotoDelDespues_traeElPlazoYLasFotosQueFaltan() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    guardarFotos(ot, MomentoFoto.ANTES);

    detalle(ot)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cerradaEnPlazo").value(true))
        .andExpect(jsonPath("$.fotosFaltantes").value(contains("DESPUES")))
        .andExpect(jsonPath("$.observacion").value(nullValue()))
        .andExpect(jsonPath("$.validadaEn").value(nullValue()));
  }

  @Test
  void detalle_observada_traeLaObservacionYSinFotosFaltantes() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    observar(jefatura, ot, "Falta la foto del después").andExpect(status().isOk());

    detalle(ot)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.observacion.motivo").value("Falta la foto del después"))
        .andExpect(jsonPath("$.observacion.observadaEn").isNotEmpty())
        .andExpect(jsonPath("$.fotosFaltantes").isEmpty());
  }

  @Test
  void detalle_validada_traeLaFechaDeValidacion() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));
    validar(jefatura, ot, "{\"aceptarSinFotos\": true}").andExpect(status().isOk());

    detalle(ot)
        .andExpect(jsonPath("$.validadaEn").isNotEmpty())
        .andExpect(jsonPath("$.cerradaEnPlazo").value(true))
        .andExpect(jsonPath("$.fotosFaltantes").isEmpty());
  }

  // --- Permisos ---

  @Test
  void operadorYTecnico_403EnValidarObservarYPorValidar() throws Exception {
    OrdenTrabajo ot = guardarCerrada(Duration.ofHours(1));

    for (UsuarioAutenticado quien : List.of(operador, tecnicoNorte)) {
      validar(quien, ot, "{\"aceptarSinFotos\": true}").andExpect(status().isForbidden());
      observar(quien, ot, "No").andExpect(status().isForbidden());
      mvc.perform(get(POR_VALIDAR).with(user(quien))).andExpect(status().isForbidden());
    }
    assertThat(estadoEnLaBase(ot)).isEqualTo(EstadoOT.CERRADA_TECNICA);
  }

  // --- Auxiliares ---

  private ResultActions validar(UsuarioAutenticado quien, OrdenTrabajo ot, String cuerpo)
      throws Exception {
    var pedido = post(ORDENES + "/" + ot.getId() + "/validar").with(user(quien)).with(csrf());
    if (cuerpo != null) {
      pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }
    return mvc.perform(pedido);
  }

  private ResultActions observar(UsuarioAutenticado quien, OrdenTrabajo ot, String motivo)
      throws Exception {
    return mvc.perform(
        post(ORDENES + "/" + ot.getId() + "/observar")
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\": \"%s\"}".formatted(motivo)));
  }

  private ResultActions detalle(OrdenTrabajo ot) throws Exception {
    return mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(jefatura)));
  }

  private EstadoOT estadoEnLaBase(OrdenTrabajo ot) {
    return ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado();
  }

  /** Registros de la OT con esa acción: quién, el estado nuevo y el motivo (como texto). */
  private List<Map<String, Object>> bitacora(OrdenTrabajo ot, String accion) {
    return jdbc.queryForList(
        "SELECT id_usuario, valor_nuevo ->> 'estado' AS estado_nuevo,"
            + " valor_nuevo ->> 'motivo' AS motivo FROM registro_auditoria"
            + " WHERE entidad = 'ORDEN_TRABAJO' AND id_entidad = ? AND accion = ?",
        ot.getId(),
        accion);
  }

  private void guardarFotos(OrdenTrabajo ot, MomentoFoto... momentos) {
    for (MomentoFoto momento : momentos) {
      adjuntoRepository.save(
          Adjunto.fotoDeCierre(
              ot.getId(),
              momento,
              momento.name().toLowerCase() + ".jpg",
              TipoImagen.JPEG,
              1024,
              "prueba/" + UUID.randomUUID() + ".jpg",
              tecnicoNorte.getId()));
    }
  }

  /** Cerrada técnicamente hace {@code haceCuanto}, dentro del plazo MEDIA de MTOP. */
  private OrdenTrabajo guardarCerrada(Duration haceCuanto) {
    Instant cerrada = Instant.now().minus(haceCuanto).truncatedTo(ChronoUnit.MICROS);
    return guardarOt(cerrada.minus(Duration.ofHours(1)), cerrada);
  }

  private OrdenTrabajo guardarEnProceso() {
    return guardarOt(Instant.now().minus(Duration.ofHours(1)), null);
  }

  /**
   * OT correctiva MEDIA de MTOP generada en {@code generada}, asignada a Norte e iniciada; cerrada
   * en {@code cerrada} si no es null.
   */
  private OrdenTrabajo guardarOt(Instant generada, Instant cerrada) {
    Instant desde = generada.truncatedTo(ChronoUnit.MICROS);
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
    OrdenTrabajo nueva = incidente.generarOrdenCorrectiva(desde, regla);
    nueva.asignar(norte.getId(), LocalDate.now(), desde);
    nueva.iniciar(desde.plusSeconds(60));
    if (cerrada != null) {
      nueva.cerrarTecnicamente(CIERRE, UUID.randomUUID(), cerrada);
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
