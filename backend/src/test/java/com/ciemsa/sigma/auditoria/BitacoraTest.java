package com.ciemsa.sigma.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.CuadrillaRepository;
import com.ciemsa.sigma.administracion.CuadrillaService;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import com.ciemsa.sigma.administracion.dto.CuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bitácora (#42, HU-12) contra PostgreSQL: cada acción crítica deja su registro con el usuario que
 * la hizo y la hora del Clock (fijo en este contexto). Los registros no se pueden borrar, así que
 * cada test mira solo los de sus propias entidades.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BitacoraTest {

  /** Hora fija de todo el contexto: la bitácora tiene que usar exactamente esta. */
  private static final Instant AHORA = Instant.now().truncatedTo(ChronoUnit.MICROS);

  private static final LocalDate MANANA =
      LocalDate.ofInstant(AHORA, ZoneId.of("America/Montevideo")).plusDays(1);
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final String DOMINIO = "@bitacora.ciemsa.com";
  private static final String PREFIJO_CUADRILLA = "Cuadrilla Bitácora";

  @TestConfiguration
  static class RelojFijo {
    @Bean
    @Primary
    Clock relojFijo() {
      return Clock.fixed(AHORA, ZoneOffset.UTC);
    }
  }

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private TransactionTemplate transacciones;
  @Autowired private AuditoriaService auditoriaService;
  @Autowired private CuadrillaService cuadrillaService;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;

  private MockMvc mvc;
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
    norte = cuadrillaRepository.save(Cuadrilla.crear(PREFIJO_CUADRILLA + " Norte", null));
    sur = cuadrillaRepository.save(Cuadrilla.crear(PREFIJO_CUADRILLA + " Sur", null));
    jefatura = enSesion(guardarUsuario("Juana Jefa", "jefa" + DOMINIO, Rol.JEFATURA, null));
    operador = enSesion(guardarUsuario("Óscar Operador", "oper" + DOMINIO, Rol.OPERADOR, null));
    tecnico = enSesion(guardarUsuario("Tania Técnica", "tec" + DOMINIO, Rol.TECNICO, norte));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-BITACORA-1",
                    "Cruce de la bitácora",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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
    jdbc.update("DELETE FROM usuario WHERE correo LIKE ?", "%" + DOMINIO);
    jdbc.update("DELETE FROM cuadrilla WHERE nombre LIKE ?", PREFIJO_CUADRILLA + "%");
  }

  // --- Incidentes ---

  @Test
  void registrarIncidente_dejaElRegistroYLaGeneracionDeLaOtConElOperador() throws Exception {
    JsonNode respuesta = registrarIncidente(operador, null);
    long idIncidente = respuesta.get("id").asLong();
    long idOt = respuesta.get("ordenTrabajo").get("id").asLong();

    assertThat(registros(EntidadAuditada.INCIDENTE, idIncidente))
        .singleElement()
        .satisfies(r -> esDe(r, AccionAuditada.REGISTRO, operador))
        .satisfies(r -> sinValores(r));
    assertThat(registros(EntidadAuditada.ORDEN_TRABAJO, idOt))
        .singleElement()
        .satisfies(r -> esDe(r, AccionAuditada.GENERACION, operador))
        .satisfies(r -> assertThat(r.anterior()).isNull())
        .satisfies(r -> assertThat(r.nuevo().get("idIncidente").asLong()).isEqualTo(idIncidente));
  }

  @Test
  void incidenteEnEspera_cerrarSinOt_quedaConQuienLoCerro() throws Exception {
    long idIncidente = registrarIncidente(operador, "UTE").get("id").asLong();

    mvc.perform(
            post("/api/v1/incidentes/{id}/cerrar-sin-ot", idIncidente)
                .with(user(jefatura))
                .with(csrf()))
        .andExpect(status().isOk());

    assertThat(registros(EntidadAuditada.INCIDENTE, idIncidente))
        .extracting(Registro::accion)
        .containsExactly(AccionAuditada.REGISTRO, AccionAuditada.CIERRE_SIN_OT);
    Registro cierre = registros(EntidadAuditada.INCIDENTE, idIncidente).get(1);
    esDe(cierre, AccionAuditada.CIERRE_SIN_OT, jefatura);
    assertThat(cierre.nuevo().get("estado").asString()).isEqualTo("CERRADO_SIN_OT");
  }

  @Test
  void incidenteEnEspera_generarOt_laGeneracionEsDeQuienLaGenero() throws Exception {
    long idIncidente = registrarIncidente(operador, "UTE").get("id").asLong();

    String cuerpo =
        mvc.perform(
                post("/api/v1/incidentes/{id}/generar-ot", idIncidente)
                    .with(user(jefatura))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long idOt = jsonMapper.readTree(cuerpo).get("ordenTrabajo").get("id").asLong();

    assertThat(registros(EntidadAuditada.ORDEN_TRABAJO, idOt))
        .singleElement()
        .satisfies(r -> esDe(r, AccionAuditada.GENERACION, jefatura));
  }

  // --- Órdenes de trabajo ---

  @Test
  void asignarYReasignar_cambioDeEstadoAsignacionYReasignacionConValorAnterior() throws Exception {
    long idOt = registrarIncidente(operador, null).get("ordenTrabajo").get("id").asLong();

    asignar(idOt, norte.getId(), MANANA).andExpect(status().isOk());
    asignar(idOt, sur.getId(), MANANA.plusDays(1)).andExpect(status().isOk());

    List<Registro> registros = registros(EntidadAuditada.ORDEN_TRABAJO, idOt);
    assertThat(registros)
        .extracting(Registro::accion)
        .containsExactly(
            AccionAuditada.GENERACION,
            AccionAuditada.CAMBIO_ESTADO,
            AccionAuditada.ASIGNACION,
            AccionAuditada.REASIGNACION);
    Registro cambio = registros.get(1);
    esDe(cambio, AccionAuditada.CAMBIO_ESTADO, jefatura);
    assertThat(cambio.anterior().get("estado").asString()).isEqualTo("PENDIENTE");
    assertThat(cambio.nuevo().get("estado").asString()).isEqualTo("ASIGNADA");
    Registro asignacion = registros.get(2);
    esDe(asignacion, AccionAuditada.ASIGNACION, jefatura);
    assertThat(asignacion.anterior()).isNull();
    assertThat(asignacion.nuevo().get("idCuadrilla").asLong()).isEqualTo(norte.getId());
    assertThat(asignacion.nuevo().get("fechaEjecucion").asString()).isEqualTo(MANANA.toString());
    Registro reasignacion = registros.get(3);
    esDe(reasignacion, AccionAuditada.REASIGNACION, jefatura);
    assertThat(reasignacion.anterior().get("idCuadrilla").asLong()).isEqualTo(norte.getId());
    assertThat(reasignacion.anterior().get("fechaEjecucion").asString())
        .isEqualTo(MANANA.toString());
    assertThat(reasignacion.nuevo().get("idCuadrilla").asLong()).isEqualTo(sur.getId());
  }

  @Test
  void pausarYReanudar_conMotivoYElTecnicoQueLoHizo() throws Exception {
    long idOt = registrarIncidente(operador, null).get("ordenTrabajo").get("id").asLong();
    asignar(idOt, norte.getId(), MANANA).andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/ordenes/{id}/pausar", idOt)
                .with(user(tecnico))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "TERCERO", "detalle": "UTE"}
                    """))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/ordenes/{id}/reanudar", idOt).with(user(tecnico)).with(csrf()))
        .andExpect(status().isOk());

    List<Registro> registros = registros(EntidadAuditada.ORDEN_TRABAJO, idOt);
    Registro pausa = unico(registros, AccionAuditada.PAUSA);
    esDe(pausa, AccionAuditada.PAUSA, tecnico);
    assertThat(pausa.nuevo().get("motivo").asString()).isEqualTo("TERCERO");
    assertThat(pausa.nuevo().get("detalle").asString()).isEqualTo("UTE");
    Registro reanudacion = unico(registros, AccionAuditada.REANUDACION);
    esDe(reanudacion, AccionAuditada.REANUDACION, tecnico);
    assertThat(reanudacion.nuevo().get("motivo").asString()).isEqualTo("TERCERO");
    assertThat(registros)
        .filteredOn(r -> r.accion() == AccionAuditada.CAMBIO_ESTADO)
        .extracting(r -> r.nuevo().get("estado").asString())
        .containsExactly("ASIGNADA", "EN_PAUSA", "ASIGNADA");
  }

  @Test
  void anular_cambioDeEstadoYAnulacionConMotivo() throws Exception {
    long idOt = registrarIncidente(operador, null).get("ordenTrabajo").get("id").asLong();

    mvc.perform(
            post("/api/v1/ordenes/{id}/anular", idOt)
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"motivo": "  Duplicada  "}
                    """))
        .andExpect(status().isOk());

    List<Registro> registros = registros(EntidadAuditada.ORDEN_TRABAJO, idOt);
    Registro anulacion = unico(registros, AccionAuditada.ANULACION);
    esDe(anulacion, AccionAuditada.ANULACION, jefatura);
    assertThat(anulacion.nuevo().get("motivo").asString()).isEqualTo("Duplicada");
    assertThat(registros)
        .filteredOn(r -> r.accion() == AccionAuditada.CAMBIO_ESTADO)
        .singleElement()
        .satisfies(r -> assertThat(r.nuevo().get("estado").asString()).isEqualTo("ANULADA"));
  }

  @Test
  void asignacionRechazada_todoONada_noDejaRegistros() throws Exception {
    long idOt = registrarIncidente(operador, null).get("ordenTrabajo").get("id").asLong();

    mvc.perform(
            post("/api/v1/ordenes/asignar")
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"idsOrdenTrabajo": [%d, 999999999], "idCuadrilla": %d, "fechaEjecucion": "%s"}
                    """
                        .formatted(idOt, norte.getId(), MANANA)))
        .andExpect(status().isNotFound());

    assertThat(registros(EntidadAuditada.ORDEN_TRABAJO, idOt))
        .extracting(Registro::accion)
        .containsExactly(AccionAuditada.GENERACION);
  }

  // --- Usuarios (HU-01) ---

  @Test
  void usuarios_altaEdicionRestablecerDesactivarYActivar_sinContrasenaNiHash() throws Exception {
    String alta =
        mvc.perform(
                post("/api/v1/usuarios")
                    .with(user(jefatura))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"nombre": "Nuevo Operador", "correo": "nuevo%s",
                         "password": "contrasenaSecreta1", "rol": "OPERADOR"}
                        """
                            .formatted(DOMINIO)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = jsonMapper.readTree(alta).get("id").asLong();
    mvc.perform(
            put("/api/v1/usuarios/{id}", id)
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre": "Nueva Técnica", "correo": "nuevo%s", "rol": "TECNICO",
                     "idCuadrilla": %d}
                    """
                        .formatted(DOMINIO, norte.getId())))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/usuarios/{id}/restablecer-password", id)
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"password": "otraContrasena2"}
                    """))
        .andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/usuarios/{id}/desactivar", id).with(user(jefatura)).with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/usuarios/{id}/activar", id).with(user(jefatura)).with(csrf()))
        .andExpect(status().is2xxSuccessful());

    List<Registro> registros = registros(EntidadAuditada.USUARIO, id);
    assertThat(registros)
        .extracting(Registro::accion)
        .containsExactly(
            AccionAuditada.ALTA,
            AccionAuditada.MODIFICACION,
            AccionAuditada.RESTABLECIMIENTO_CONTRASENA,
            AccionAuditada.DESACTIVACION,
            AccionAuditada.ACTIVACION);
    assertThat(registros).allSatisfy(r -> esDe(r, r.accion(), jefatura));
    Registro altaRegistrada = registros.get(0);
    assertThat(altaRegistrada.anterior()).isNull();
    assertThat(altaRegistrada.nuevo().get("nombre").asString()).isEqualTo("Nuevo Operador");
    assertThat(altaRegistrada.nuevo().get("rol").asString()).isEqualTo("OPERADOR");
    Registro edicion = registros.get(1);
    assertThat(edicion.anterior().get("rol").asString()).isEqualTo("OPERADOR");
    assertThat(edicion.nuevo().get("rol").asString()).isEqualTo("TECNICO");
    assertThat(edicion.nuevo().get("idCuadrilla").asLong()).isEqualTo(norte.getId());
    sinValores(registros.get(2));
    assertThat(registros.get(3).nuevo().get("activo").asBoolean()).isFalse();
    assertThat(registros.get(4).nuevo().get("activo").asBoolean()).isTrue();
    String hash = usuarioRepository.findById(id).orElseThrow().getPasswordHash();
    assertThat(textoDeLosValores(EntidadAuditada.USUARIO, id))
        .doesNotContain(hash, "$2", "contrasena", "password", "Secreta", "otraContrasena2");
  }

  // --- Cuadrillas ---

  @Test
  void cuadrillas_altaEdicionDesactivarYActivar() throws Exception {
    String alta =
        mvc.perform(
                post("/api/v1/cuadrillas")
                    .with(user(jefatura))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"nombre": "%s Este", "zona": "Este"}
                        """
                            .formatted(PREFIJO_CUADRILLA)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = jsonMapper.readTree(alta).get("id").asLong();
    mvc.perform(
            put("/api/v1/cuadrillas/{id}", id)
                .with(user(jefatura))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre": "%s Este", "zona": "Costa"}
                    """
                        .formatted(PREFIJO_CUADRILLA)))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/cuadrillas/{id}/desactivar", id).with(user(jefatura)).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    mvc.perform(post("/api/v1/cuadrillas/{id}/activar", id).with(user(jefatura)).with(csrf()))
        .andExpect(status().is2xxSuccessful());

    List<Registro> registros = registros(EntidadAuditada.CUADRILLA, id);
    assertThat(registros)
        .extracting(Registro::accion)
        .containsExactly(
            AccionAuditada.ALTA,
            AccionAuditada.MODIFICACION,
            AccionAuditada.DESACTIVACION,
            AccionAuditada.ACTIVACION);
    assertThat(registros).allSatisfy(r -> esDe(r, r.accion(), jefatura));
    assertThat(registros.get(1).anterior().get("zona").asString()).isEqualTo("Este");
    assertThat(registros.get(1).nuevo().get("zona").asString()).isEqualTo("Costa");
  }

  // --- Activar o desactivar sin cambio de estado: no se registra nada ---

  @Test
  void activarUsuarioYaActivo_noDejaRegistro() throws Exception {
    mvc.perform(
            post("/api/v1/usuarios/{id}/activar", operador.getId())
                .with(user(jefatura))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertThat(registros(EntidadAuditada.USUARIO, operador.getId())).isEmpty();
  }

  @Test
  void desactivarUsuarioYaInactivo_noDejaRegistro() throws Exception {
    jdbc.update("UPDATE usuario SET activo = false WHERE id_usuario = ?", operador.getId());

    mvc.perform(
            post("/api/v1/usuarios/{id}/desactivar", operador.getId())
                .with(user(jefatura))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertThat(registros(EntidadAuditada.USUARIO, operador.getId())).isEmpty();
  }

  @Test
  void activarCuadrillaYaActiva_noDejaRegistro() throws Exception {
    mvc.perform(
            post("/api/v1/cuadrillas/{id}/activar", norte.getId())
                .with(user(jefatura))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertThat(registros(EntidadAuditada.CUADRILLA, norte.getId())).isEmpty();
  }

  @Test
  void desactivarCuadrillaYaInactiva_noDejaRegistro() throws Exception {
    jdbc.update("UPDATE cuadrilla SET activa = false WHERE id_cuadrilla = ?", sur.getId());

    mvc.perform(
            post("/api/v1/cuadrillas/{id}/desactivar", sur.getId())
                .with(user(jefatura))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    assertThat(registros(EntidadAuditada.CUADRILLA, sur.getId())).isEmpty();
  }

  // --- Misma transacción ---

  @Test
  void accionQueVuelveAtras_noDejaRegistro() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(jefatura, null, jefatura.getAuthorities()));
    AtomicReference<Long> idCreada = new AtomicReference<>();

    assertThatThrownBy(
            () ->
                transacciones.executeWithoutResult(
                    tx -> {
                      CuadrillaResponse creada =
                          cuadrillaService.crear(
                              new CuadrillaRequest(PREFIJO_CUADRILLA + " Revertida", null));
                      idCreada.set(creada.id());
                      assertThat(registros(EntidadAuditada.CUADRILLA, creada.id())).hasSize(1);
                      throw new IllegalStateException("falla después de la acción");
                    }))
        .hasMessage("falla después de la acción");

    assertThat(registros(EntidadAuditada.CUADRILLA, idCreada.get())).isEmpty();
    assertThat(cuadrillaRepository.findById(idCreada.get())).isEmpty();
  }

  @Test
  void siFallaLaBitacora_fallaLaAccion() throws Exception {
    // Un usuario en sesión que no existe en la base: la bitácora no encuentra su nombre.
    UsuarioAutenticado fantasma =
        new UsuarioAutenticado(
            999_999_999L, "fantasma" + DOMINIO, "Fantasma", Rol.JEFATURA, null, true, null, null);

    mvc.perform(
            post("/api/v1/cuadrillas")
                .with(user(fantasma))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"nombre": "%s Fantasma"}
                    """
                        .formatted(PREFIJO_CUADRILLA)))
        .andExpect(status().isInternalServerError());

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cuadrilla WHERE nombre = ?",
                Long.class,
                PREFIJO_CUADRILLA + " Fantasma"))
        .isZero();
  }

  @Test
  void registrarSinTransaccion_falla() {
    assertThatThrownBy(
            () ->
                auditoriaService.registrar(
                    EntidadAuditada.CUADRILLA,
                    norte.getId(),
                    AccionAuditada.ALTA,
                    jefatura.getId(),
                    AHORA,
                    null,
                    null))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  // --- Consulta: GET /api/v1/bitacora ---

  @Test
  void consultar_operadorYTecnico_reciben403() throws Exception {
    mvc.perform(get("/api/v1/bitacora").with(user(operador))).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/bitacora").with(user(tecnico))).andExpect(status().isForbidden());
  }

  @Test
  void consultar_jefaturaFiltraPorEntidadMasRecientePrimeroYPaginado() throws Exception {
    JsonNode incidente = registrarIncidente(operador, null);
    long idIncidente = incidente.get("id").asLong();
    long idOt = incidente.get("ordenTrabajo").get("id").asLong();
    asignar(idOt, norte.getId(), MANANA).andExpect(status().isOk());
    asignar(idOt, sur.getId(), MANANA).andExpect(status().isOk());

    consultar("ORDEN_TRABAJO", idOt, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(4))
        .andExpect(jsonPath("$.tamanio").value(20))
        .andExpect(jsonPath("$.contenido[0].accion").value("REASIGNACION"))
        .andExpect(jsonPath("$.contenido[0].entidad").value("ORDEN_TRABAJO"))
        .andExpect(jsonPath("$.contenido[0].idEntidad").value(idOt))
        .andExpect(jsonPath("$.contenido[0].idUsuario").value(jefatura.getId()))
        .andExpect(jsonPath("$.contenido[0].nombreUsuario").value("Juana Jefa"))
        .andExpect(jsonPath("$.contenido[0].fechaHora").value(AHORA.toString()))
        .andExpect(jsonPath("$.contenido[0].valorAnterior.idCuadrilla").value(norte.getId()))
        .andExpect(jsonPath("$.contenido[0].valorNuevo.idCuadrilla").value(sur.getId()))
        .andExpect(jsonPath("$.contenido[3].accion").value("GENERACION"))
        .andExpect(jsonPath("$.contenido[3].valorAnterior").isEmpty());

    consultar("ORDEN_TRABAJO", idOt, "&size=1&page=1")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido.length()").value(1))
        .andExpect(jsonPath("$.contenido[0].accion").value("ASIGNACION"))
        .andExpect(jsonPath("$.pagina").value(1))
        .andExpect(jsonPath("$.totalPaginas").value(4));

    // El mismo filtro con la otra entidad trae solo lo del incidente.
    consultar("INCIDENTE", idIncidente, "")
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].accion").value("REGISTRO"))
        .andExpect(jsonPath("$.contenido[0].idUsuario").value(operador.getId()));
    consultar("ORDEN_TRABAJO", -1, "").andExpect(jsonPath("$.totalElementos").value(0));
  }

  @Test
  void consultar_sinFiltros_devuelveTodoYRespetaElMaximoDe100() throws Exception {
    registrarIncidente(operador, null);

    mvc.perform(get("/api/v1/bitacora?size=500").with(user(jefatura)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tamanio").value(100));
  }

  // --- Helpers ---

  private record Registro(
      AccionAuditada accion,
      Long idUsuario,
      String nombreUsuario,
      Instant fechaHora,
      JsonNode anterior,
      JsonNode nuevo) {}

  /** Los registros de una entidad, en el orden en que se grabaron. */
  private List<Registro> registros(EntidadAuditada entidad, Long idEntidad) {
    return jdbc.query(
        """
        SELECT accion, id_usuario, nombre_usuario, fecha_hora,
               valor_anterior::text AS anterior, valor_nuevo::text AS nuevo
        FROM registro_auditoria WHERE entidad = ? AND id_entidad = ?
        ORDER BY id_registro_auditoria
        """,
        (rs, i) ->
            new Registro(
                AccionAuditada.valueOf(rs.getString("accion")),
                rs.getLong("id_usuario"),
                rs.getString("nombre_usuario"),
                rs.getObject("fecha_hora", Timestamp.class).toInstant(),
                json(rs.getString("anterior")),
                json(rs.getString("nuevo"))),
        entidad.name(),
        idEntidad);
  }

  private String textoDeLosValores(EntidadAuditada entidad, Long idEntidad) {
    return String.join(
        " ",
        jdbc.queryForList(
            """
            SELECT coalesce(valor_anterior::text, '') || coalesce(valor_nuevo::text, '')
            FROM registro_auditoria WHERE entidad = ? AND id_entidad = ?
            """,
            String.class,
            entidad.name(),
            idEntidad));
  }

  private JsonNode json(String texto) {
    return texto == null ? null : jsonMapper.readTree(texto);
  }

  private static void esDe(Registro r, AccionAuditada accion, UsuarioAutenticado quien) {
    assertThat(r.accion()).isEqualTo(accion);
    assertThat(r.idUsuario()).isEqualTo(quien.getId());
    assertThat(r.nombreUsuario()).isEqualTo(quien.getNombre());
    assertThat(r.fechaHora()).isEqualTo(AHORA);
  }

  private static void sinValores(Registro r) {
    assertThat(r.anterior()).isNull();
    assertThat(r.nuevo()).isNull();
  }

  private static Registro unico(List<Registro> registros, AccionAuditada accion) {
    List<Registro> deLaAccion = registros.stream().filter(r -> r.accion() == accion).toList();
    assertThat(deLaAccion).hasSize(1);
    return deLaAccion.get(0);
  }

  private JsonNode registrarIncidente(UsuarioAutenticado quien, String tercero) throws Exception {
    Map<String, Object> cuerpo = new LinkedHashMap<>();
    cuerpo.put("idInstalacion", idInstalacion);
    cuerpo.put("origen", "MONITOREO");
    cuerpo.put("idCategoriaFalla", idCategoria);
    cuerpo.put("descripcion", "Falla de prueba");
    cuerpo.put("tercero", tercero);
    String respuesta =
        mvc.perform(
                post("/api/v1/incidentes")
                    .with(user(quien))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(cuerpo)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(respuesta);
  }

  private ResultActions asignar(long idOt, Long idCuadrilla, LocalDate fecha) throws Exception {
    return mvc.perform(
        post("/api/v1/ordenes/asignar")
            .with(user(jefatura))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"idsOrdenTrabajo": [%d], "idCuadrilla": %d, "fechaEjecucion": "%s"}
                """
                    .formatted(idOt, idCuadrilla, fecha)));
  }

  private ResultActions consultar(String entidad, long idEntidad, String extra) throws Exception {
    return mvc.perform(
        get("/api/v1/bitacora?entidad=" + entidad + "&idEntidad=" + idEntidad + extra)
            .with(user(jefatura)));
  }

  private Usuario guardarUsuario(String nombre, String correo, Rol rol, Cuadrilla cuadrilla) {
    return usuarioRepository.save(Usuario.crear(nombre, correo, HASH, rol, cuadrilla));
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
