package com.ciemsa.sigma.ot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.ot.infrastructure.ReglaSlaRepository;
import com.ciemsa.sigma.shared.archivos.AlmacenArchivos;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Iniciar y cerrar la OT (#88, HU-07, CU-3) contra PostgreSQL. La OT es una correctiva MEDIA de
 * MTOP asignada a la cuadrilla Norte; las acciones de la Jefatura (anular, reasignar) pasan por sus
 * endpoints.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class EjecucionOtControllerTest {

  private static final String ORDENES = "/api/v1/ordenes";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3, 4);
  private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 5, 6);

  @Autowired private AlmacenArchivos almacenArchivos;
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
  private UsuarioAutenticado jefatura;
  private UsuarioAutenticado tecnicoNorte;
  private UsuarioAutenticado tecnicoSur;
  private OrdenTrabajo ot;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    norte = guardarCuadrilla("Cuadrilla Ejecución Norte");
    sur = guardarCuadrilla("Cuadrilla Ejecución Sur");
    operador =
        enSesion(guardarUsuario("Operador", "operador.ejecucion@ciemsa.com", Rol.OPERADOR, null));
    jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.ejecucion@ciemsa.com", Rol.JEFATURA, null));
    tecnicoNorte =
        enSesion(guardarUsuario("Técnico Norte", "norte.ejecucion@ciemsa.com", Rol.TECNICO, norte));
    tecnicoSur =
        enSesion(guardarUsuario("Técnico Sur", "sur.ejecucion@ciemsa.com", Rol.TECNICO, sur));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-EJEC-1",
                    "Cruce de ejecución",
                    "Ruta 8",
                    new BigDecimal("-34.790000"),
                    new BigDecimal("-56.060000"),
                    mtop))
            .getId();
    ot = guardarOtAsignadaANorte();
  }

  @AfterEach
  void tearDown() {
    String ordenesDeLaInstalacion =
        " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)";
    String fotosDeLasOrdenes = " FROM adjunto WHERE id_orden_trabajo IN" + ordenesDeLaInstalacion;
    jdbc.queryForList("SELECT ubicacion" + fotosDeLasOrdenes, String.class, idInstalacion)
        .forEach(almacenArchivos::borrar);
    jdbc.update("DELETE" + fotosDeLasOrdenes, idInstalacion);
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

  // --- Iniciar (paso 2) ---

  @Test
  void iniciar_asignada_pasaAEnProcesoConLaHoraDeInicio() throws Exception {
    Instant antes = Instant.now().truncatedTo(ChronoUnit.MICROS);

    iniciar(tecnicoNorte, ot)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_PROCESO"))
        .andExpect(jsonPath("$.iniciadaEn").isNotEmpty())
        .andExpect(jsonPath("$.cierre").value(nullValue()));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getEstado()).isEqualTo(EstadoOT.EN_PROCESO);
    assertThat(guardada.getIniciadaEn()).isAfterOrEqualTo(antes);
    assertThat(eventos.stream(EstadoOrdenCambiado.class))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.estadoNuevo()).isEqualTo(EstadoOT.EN_PROCESO);
              assertThat(e.idUsuario()).isEqualTo(tecnicoNorte.getId());
            });
    assertThat(cambiosDeEstadoEnBitacora()).isEqualTo(1);
  }

  @Test
  void iniciar_yaEnProceso_devuelve400ConElEstadoActual() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    iniciar(tecnicoNorte, ot)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("EN_PROCESO"));
  }

  @Test
  void iniciar_tecnicoDeOtraCuadrilla_devuelve403() throws Exception {
    iniciar(tecnicoSur, ot)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.ASIGNADA);
  }

  @Test
  void iniciar_inexistente_devuelve404() throws Exception {
    mvc.perform(post(ORDENES + "/999999/iniciar").with(user(tecnicoNorte)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  // --- Cerrar (pasos 6 a 10) ---

  @Test
  void cerrar_enProceso_cierraConLosDatosYLaFecha() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    UUID operacion = UUID.randomUUID();

    cerrar(tecnicoNorte, ot, operacion, "  Lámpara roja quemada ", "Cambio de lámpara", "Ok")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CERRADA_TECNICA"))
        .andExpect(jsonPath("$.cierre.diagnostico").value("Lámpara roja quemada"))
        .andExpect(jsonPath("$.cierre.solucion").value("Cambio de lámpara"))
        .andExpect(jsonPath("$.cierre.observaciones").value("Ok"))
        .andExpect(jsonPath("$.cierre.cerradaEn").isNotEmpty())
        .andExpect(jsonPath("$.vencida").value(false));

    OrdenTrabajo guardada = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(guardada.getEstado()).isEqualTo(EstadoOT.CERRADA_TECNICA);
    assertThat(guardada.getCerradaEn()).isNotNull();
    assertThat(guardada.getIdOperacionCierre()).isEqualTo(operacion);
    assertThat(eventos.stream(EstadoOrdenCambiado.class).map(EstadoOrdenCambiado::estadoNuevo))
        .containsExactly(EstadoOT.EN_PROCESO, EstadoOT.CERRADA_TECNICA);
    assertThat(cambiosDeEstadoEnBitacora()).isEqualTo(2);
  }

  @Test
  void cerrar_reintentoConLaMismaOperacion_devuelveElResultadoAnteriorSinRepetir()
      throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    UUID operacion = UUID.randomUUID();
    cerrar(tecnicoNorte, ot, operacion, "Lámpara quemada", "Cambio", null)
        .andExpect(status().isOk());
    Instant cerradaEn = ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getCerradaEn();

    // El reintento trae otros textos (el celular los editó): no cambia nada
    cerrar(tecnicoNorte, ot, operacion, "Otro diagnóstico", "Otra solución", null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CERRADA_TECNICA"))
        .andExpect(jsonPath("$.cierre.diagnostico").value("Lámpara quemada"));

    assertThat(ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getCerradaEn())
        .isEqualTo(cerradaEn);
    assertThat(eventos.stream(EstadoOrdenCambiado.class)).hasSize(2);
    assertThat(cambiosDeEstadoEnBitacora()).isEqualTo(2);
  }

  @Test
  void cerrar_yaCerradaConOtraOperacion_devuelve400ConElEstadoActual() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isOk());

    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("CERRADA_TECNICA"));
  }

  @Test
  void cerrar_sinIniciar_devuelve400ConElEstadoActual() throws Exception {
    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("ASIGNADA"));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.ASIGNADA);
  }

  @Test
  void cerrar_enPausa_devuelve400() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    mvc.perform(
            post(ORDENES + "/" + ot.getId() + "/pausar")
                .with(user(tecnicoNorte))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"CLIMA\"}"))
        .andExpect(status().isOk());

    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.estadoActual").value("EN_PAUSA"));
  }

  @Test
  void cerrar_sinDiagnosticoNiSolucionNiOperacion_devuelve400ConLosCampos() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos("{\"diagnostico\": \" \", \"observaciones\": \"Nada\"}"),
            List.of(foto("antes.jpg", JPEG)),
            List.of("ANTES"))
        .andExpect(status().isBadRequest());

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.EN_PROCESO);
  }

  @Test
  void cerrar_operacionUsadaEnOtraOt_devuelve409() throws Exception {
    OrdenTrabajo otra = guardarOtAsignadaANorte();
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    iniciar(tecnicoNorte, otra).andExpect(status().isOk());
    UUID operacion = UUID.randomUUID();
    cerrar(tecnicoNorte, ot, operacion, "Diagnóstico", "Solución", null).andExpect(status().isOk());

    cerrar(tecnicoNorte, otra, operacion, "Diagnóstico", "Solución", null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errores/operacion-repetida"));
  }

  // --- 8b: la OT cambió mientras el técnico trabajaba ---

  @Test
  void cerrar_otReasignadaAOtraCuadrilla_devuelve403() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    // La Jefatura no reasigna una OT en proceso: se simula que otra cuadrilla la tomó
    jdbc.update(
        "UPDATE orden_trabajo SET id_cuadrilla = ? WHERE id_orden_trabajo = ?",
        sur.getId(),
        ot.getId());

    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isForbidden());
  }

  @Test
  void iniciar_otAnulada_devuelve400ConElEstadoActual() throws Exception {
    anular(ot);

    iniciar(tecnicoNorte, ot)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/transicion-invalida"))
        .andExpect(jsonPath("$.estadoActual").value("ANULADA"));
  }

  @Test
  void iniciar_otReasignadaAOtraCuadrilla_devuelve403YLaOtraCuadrillaLaInicia() throws Exception {
    reasignar(ot, sur);

    iniciar(tecnicoNorte, ot).andExpect(status().isForbidden());
    iniciar(tecnicoSur, ot).andExpect(status().isOk());
  }

  // --- Permisos por rol ---

  @Test
  void operadorYJefatura_devuelven403() throws Exception {
    for (UsuarioAutenticado quien : List.of(operador, jefatura)) {
      iniciar(quien, ot).andExpect(status().isForbidden());
      cerrar(quien, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
          .andExpect(status().isForbidden());
    }
    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.ASIGNADA);
  }

  @Test
  void sinSesion_devuelve401() throws Exception {
    mvc.perform(post(ORDENES + "/" + ot.getId() + "/iniciar").with(csrf()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            multipart(ORDENES + "/" + ot.getId() + "/cerrar")
                .file(datos(cuerpoCierre(UUID.randomUUID(), "D", "S", null)))
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void detalle_otCerrada_traeElCierreParaLaJefatura() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isOk());

    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cierre.diagnostico").value("Diagnóstico"))
        .andExpect(jsonPath("$.cierre.observaciones").value(nullValue()))
        .andExpect(jsonPath("$.iniciadaEn").isNotEmpty())
        .andExpect(jsonPath("$.tiempos").value(nullValue()));
  }

  // --- Auxiliares ---

  private ResultActions iniciar(UsuarioAutenticado quien, OrdenTrabajo orden) throws Exception {
    return mvc.perform(
        post(ORDENES + "/" + orden.getId() + "/iniciar").with(user(quien)).with(csrf()));
  }

  /** El cierre de siempre, con una foto de «Antes» (HU-08 exige al menos una). */
  private ResultActions cerrar(
      UsuarioAutenticado quien,
      OrdenTrabajo orden,
      UUID operacion,
      String diagnostico,
      String solucion,
      String observaciones)
      throws Exception {
    return enviarCierre(
        quien,
        orden,
        datos(cuerpoCierre(operacion, diagnostico, solucion, observaciones)),
        List.of(foto("antes.jpg", JPEG)),
        List.of("ANTES"));
  }

  private ResultActions enviarCierre(
      UsuarioAutenticado quien,
      OrdenTrabajo orden,
      MockMultipartFile datos,
      List<MockMultipartFile> fotos,
      List<String> momentos)
      throws Exception {
    var pedido = multipart(ORDENES + "/" + orden.getId() + "/cerrar").file(datos);
    fotos.forEach(pedido::file);
    momentos.forEach(momento -> pedido.param("momentos", momento));
    return mvc.perform(pedido.with(user(quien)).with(csrf()));
  }

  private static MockMultipartFile datos(String json) {
    return new MockMultipartFile(
        "datos", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
  }

  /** El tipo declarado es siempre image/jpeg: lo que vale es el contenido. */
  private static MockMultipartFile foto(String nombre, byte[] contenido) {
    return new MockMultipartFile("fotos", nombre, "image/jpeg", contenido);
  }

  private Integer fotosEnLaBase() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM adjunto WHERE id_orden_trabajo = ?", Integer.class, ot.getId());
  }

  private static String cuerpoCierre(
      UUID operacion, String diagnostico, String solucion, String observaciones) {
    return """
        {"idOperacion": "%s", "diagnostico": "%s", "solucion": "%s"%s}"""
        .formatted(
            operacion,
            diagnostico,
            solucion,
            observaciones == null ? "" : ", \"observaciones\": \"" + observaciones + "\"");
  }

  private void anular(OrdenTrabajo orden) throws Exception {
    mvc.perform(
            post(ORDENES + "/" + orden.getId() + "/anular")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"Duplicada\"}"))
        .andExpect(status().isOk());
  }

  private void reasignar(OrdenTrabajo orden, Cuadrilla cuadrilla) throws Exception {
    mvc.perform(
            post(ORDENES + "/asignar")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"idsOrdenTrabajo\": [%d], \"idCuadrilla\": %d, \"fechaEjecucion\": \"%s\"}"
                        .formatted(orden.getId(), cuadrilla.getId(), LocalDate.now())))
        .andExpect(status().isOk());
  }

  private EstadoOT estadoEnLaBase() {
    return ordenTrabajoRepository.findById(ot.getId()).orElseThrow().getEstado();
  }

  private long cambiosDeEstadoEnBitacora() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM registro_auditoria"
            + " WHERE entidad = 'ORDEN_TRABAJO' AND id_entidad = ? AND accion = 'CAMBIO_ESTADO'",
        Long.class,
        ot.getId());
  }

  /** OT correctiva MEDIA de MTOP, generada hace una hora y asignada a Norte para hoy. */
  private OrdenTrabajo guardarOtAsignadaANorte() {
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
    nueva.asignar(norte.getId(), LocalDate.now(), hace1h);
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

  // --- Fotos del cierre (#90, HU-08) ---

  @Test
  void cerrar_conFotos_lasGuardaConSuMomentoYSeVen() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            List.of(foto("antes.jpg", JPEG), foto("despues.png", PNG)),
            List.of("ANTES", "DESPUES"))
        .andExpect(status().isOk());

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.CERRADA_TECNICA);
    String respuesta =
        mvc.perform(get(ORDENES + "/" + ot.getId() + "/fotos").with(user(jefatura)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].momento").value("ANTES"))
            .andExpect(jsonPath("$[0].tipo").value("image/jpeg"))
            .andExpect(jsonPath("$[1].momento").value("DESPUES"))
            .andExpect(jsonPath("$[1].tipo").value("image/png"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String url = JsonPath.read(respuesta, "$[0].url");
    mvc.perform(get(url).with(user(tecnicoNorte)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(JPEG));
  }

  @Test
  void cerrar_sinFotos_400YNoCierra() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            List.of(),
            List.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"))
        .andExpect(jsonPath("$.detail").value("El cierre técnico exige al menos una foto."));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.EN_PROCESO);
  }

  @Test
  void cerrar_masDeSeisFotos_400YNoGuardaNinguna() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    List<MockMultipartFile> siete = new ArrayList<>();
    List<String> momentos = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      siete.add(foto("foto" + i + ".jpg", JPEG));
      momentos.add("DURANTE");
    }

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            siete,
            momentos)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.EN_PROCESO);
    assertThat(fotosEnLaBase()).isZero();
  }

  @Test
  void cerrar_unaQueNoEsImagen_400NoCierraNiGuardaLasDemas() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            List.of(foto("antes.jpg", JPEG), foto("texto.jpg", "hola".getBytes())),
            List.of("ANTES", "DESPUES"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));

    assertThat(estadoEnLaBase()).isEqualTo(EstadoOT.EN_PROCESO);
    assertThat(fotosEnLaBase()).isZero();
  }

  @Test
  void cerrar_momentosQueNoCoincidenConLasFotos_400() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            List.of(foto("antes.jpg", JPEG), foto("despues.jpg", JPEG)),
            List.of("ANTES"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));
  }

  @Test
  void cerrar_momentoQueNoExiste_400() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    enviarCierre(
            tecnicoNorte,
            ot,
            datos(cuerpoCierre(UUID.randomUUID(), "Diagnóstico", "Solución", null)),
            List.of(foto("antes.jpg", JPEG)),
            List.of("MIENTRAS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"));
  }

  @Test
  void cerrar_reintentoConLaMismaOperacion_noDuplicaLasFotos() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    UUID operacion = UUID.randomUUID();

    cerrar(tecnicoNorte, ot, operacion, "Diagnóstico", "Solución", null).andExpect(status().isOk());
    cerrar(tecnicoNorte, ot, operacion, "Diagnóstico", "Solución", null).andExpect(status().isOk());

    assertThat(fotosEnLaBase()).isEqualTo(1);
  }

  // --- Un técnico de otra cuadrilla ve las OTs terminadas (historial, HU-10) ---

  @Test
  void fotosDeLaOt_cerradaPorOtraCuadrilla_elTecnicoLasVe() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isOk());

    String fotos = ORDENES + "/" + ot.getId() + "/fotos";
    mvc.perform(get(fotos).with(user(tecnicoSur)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    Long idFoto =
        jdbc.queryForObject(
            "SELECT id_adjunto FROM adjunto WHERE id_orden_trabajo = ?", Long.class, ot.getId());
    mvc.perform(get(fotos + "/" + idFoto).with(user(tecnicoSur))).andExpect(status().isOk());
  }

  @Test
  void fotosDeLaOt_enCursoDeOtraCuadrilla_403() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());

    mvc.perform(get(ORDENES + "/" + ot.getId() + "/fotos").with(user(tecnicoSur)))
        .andExpect(status().isForbidden());
  }

  @Test
  void detalle_cerradaPorOtraCuadrilla_elTecnicoLaVePeroNoLaOpera() throws Exception {
    iniciar(tecnicoNorte, ot).andExpect(status().isOk());
    cerrar(tecnicoNorte, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isOk());

    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(tecnicoSur)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("CERRADA_TECNICA"));
    mvc.perform(
            post(ORDENES + "/" + ot.getId() + "/pausar")
                .with(user(tecnicoSur))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\": \"CLIMA\"}"))
        .andExpect(status().isForbidden());
    cerrar(tecnicoSur, ot, UUID.randomUUID(), "Diagnóstico", "Solución", null)
        .andExpect(status().isForbidden());
  }

  @Test
  void detalle_anulada_elTecnicoDeOtraCuadrillaLaVe() throws Exception {
    anular(ot);

    mvc.perform(get(ORDENES + "/" + ot.getId()).with(user(tecnicoSur)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("ANULADA"));
  }

  private static byte[] bytes(int... valores) {
    byte[] resultado = new byte[valores.length];
    for (int i = 0; i < valores.length; i++) {
      resultado[i] = (byte) valores[i];
    }
    return resultado;
  }
}
