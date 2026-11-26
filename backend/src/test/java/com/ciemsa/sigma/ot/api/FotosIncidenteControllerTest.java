package com.ciemsa.sigma.ot.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.ciemsa.sigma.ot.application.FotosIncidenteService;
import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.archivos.AlmacenArchivos;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Fotos del incidente (#71) contra PostgreSQL y el almacén en disco (carpeta temporal de los
 * tests). Los incidentes son de la instalación TEST-FOTOS-1.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FotosIncidenteControllerTest {

  private static final String FOTOS = "/api/v1/incidentes/{id}/fotos";
  private static final String HASH = "$2a$10$hashDePrueba";
  private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 1, 2, 3, 4);
  private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 5, 6);

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AlmacenArchivos almacenArchivos;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;

  private MockMvc mvc;
  private final List<Long> usuarios = new ArrayList<>();
  private Long idCategoria;
  private Long idInstalacion;
  private Cuadrilla cuadrilla;
  private UsuarioAutenticado operador;
  private UsuarioAutenticado tecnico;
  private Cuadrilla otraCuadrilla;
  private UsuarioAutenticado tecnicoDeOtraCuadrilla;
  private Incidente incidente;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    cuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Fotos", null));
    operador =
        enSesion(guardarUsuario("Operador", "operador.fotos@ciemsa.com", Rol.OPERADOR, null));
    tecnico =
        enSesion(guardarUsuario("Técnico", "tecnico.fotos@ciemsa.com", Rol.TECNICO, cuadrilla));
    otraCuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Fotos Sur", null));
    tecnicoDeOtraCuadrilla =
        enSesion(
            guardarUsuario(
                "Técnico Sur", "tecnico.sur.fotos@ciemsa.com", Rol.TECNICO, otraCuadrilla));
    Long mtop = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idCategoria =
        jdbc.queryForObject(
            "SELECT id_categoria_falla FROM categoria_falla WHERE nombre = 'Lámpara quemada'",
            Long.class);
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-FOTOS-1",
                    "Cruce de fotos",
                    "Ruta 1",
                    new BigDecimal("-34.780000"),
                    new BigDecimal("-56.250000"),
                    mtop))
            .getId();
    incidente = registrarIncidente();
  }

  @AfterEach
  void tearDown() {
    String delTest =
        " FROM adjunto WHERE id_incidente IN"
            + " (SELECT id_incidente FROM incidente WHERE id_instalacion = ?)";
    jdbc.queryForList("SELECT ubicacion" + delTest, String.class, idInstalacion)
        .forEach(almacenArchivos::borrar);
    jdbc.update("DELETE" + delTest, idInstalacion);
    jdbc.update(
        "DELETE FROM tramo_cuadrilla_ot WHERE id_orden_trabajo IN"
            + " (SELECT id_orden_trabajo FROM orden_trabajo WHERE id_instalacion = ?)",
        idInstalacion);
    jdbc.update("DELETE FROM orden_trabajo WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM incidente WHERE id_instalacion = ?", idInstalacion);
    jdbc.update("DELETE FROM instalacion WHERE id_instalacion = ?", idInstalacion);
    usuarios.forEach(usuarioRepository::deleteById);
    cuadrillaRepository.deleteById(cuadrilla.getId());
    cuadrillaRepository.deleteById(otraCuadrilla.getId());
  }

  // --- Subir y ver ---

  @Test
  void operador_subeDosFotos_201YSeListanYSeVen() throws Exception {
    String respuesta =
        subir(operador, incidente.getId(), foto("frente.jpg", JPEG), foto("poste.png", PNG))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].nombreOriginal").value("frente.jpg"))
            .andExpect(jsonPath("$[0].tipo").value("image/jpeg"))
            .andExpect(jsonPath("$[0].tamanioBytes").value(JPEG.length))
            .andExpect(jsonPath("$[1].tipo").value("image/png"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mvc.perform(get(FOTOS, incidente.getId()).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    String url = JsonPath.read(respuesta, "$[0].url");
    mvc.perform(get(url).with(user(operador)))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(content().bytes(JPEG));
  }

  // --- Validaciones ---

  @Test
  void textoConExtensionJpg_400FotoInvalidaYNoGuardaNinguna() throws Exception {
    byte[] texto = "no soy una foto".getBytes(StandardCharsets.UTF_8);

    subir(operador, incidente.getId(), foto("buena.png", PNG), foto("trucha.jpg", texto))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));
    sinFotos();
  }

  @Test
  void masDe10Mb_400FotoInvalida() throws Exception {
    byte[] grande = new byte[(int) FotosIncidenteService.TAMANIO_MAXIMO + 1];
    System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

    subir(operador, incidente.getId(), foto("enorme.jpg", grande))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));
    sinFotos();
  }

  @Test
  void vacia_400FotoInvalida() throws Exception {
    subir(operador, incidente.getId(), foto("vacia.jpg", new byte[0]))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/foto-invalida"));
  }

  @Test
  void cuartaFoto_400DemasiadasFotosYQuedanLasTres() throws Exception {
    subir(operador, incidente.getId(), foto("1.jpg", JPEG), foto("2.jpg", JPEG), foto("3.png", PNG))
        .andExpect(status().isCreated());

    subir(operador, incidente.getId(), foto("4.jpg", JPEG))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/demasiadas-fotos"));
    mvc.perform(get(FOTOS, incidente.getId()).with(user(operador)))
        .andExpect(jsonPath("$.length()").value(3));
  }

  // --- Permisos y existencia ---

  @Test
  void tecnico_403AlSubir() throws Exception {
    asignarOt(cuadrilla);

    subir(tecnico, incidente.getId(), foto("frente.jpg", JPEG)).andExpect(status().isForbidden());
    sinFotos();
  }

  @Test
  void tecnicoDeLaCuadrillaDeLaOt_veLasFotosYLaImagen() throws Exception {
    String url = subirUnaFoto();
    asignarOt(cuadrilla);

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnico)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get(url).with(user(tecnico)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(JPEG));
  }

  @Test
  void tecnicoDeOtraCuadrilla_otAsignada_403AlListarYAlVer() throws Exception {
    String url = subirUnaFoto();
    asignarOt(cuadrilla);

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
    mvc.perform(get(url).with(user(tecnicoDeOtraCuadrilla))).andExpect(status().isForbidden());
  }

  @Test
  void tecnicoDeOtraCuadrilla_otEnProceso_403() throws Exception {
    String url = subirUnaFoto();
    generarOt(ot -> iniciar(ot, cuadrilla));

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isForbidden());
    mvc.perform(get(url).with(user(tecnicoDeOtraCuadrilla))).andExpect(status().isForbidden());
  }

  /** HU-10: revisa el historial del cruce antes de intervenir, aunque lo haya atendido otra. */
  @ParameterizedTest
  @EnumSource(
      value = EstadoOT.class,
      names = {"CERRADA_TECNICA", "VALIDADA", "ANULADA"})
  void tecnicoDeOtraCuadrilla_otTerminada_veLasFotosYLaImagen(EstadoOT estado) throws Exception {
    String url = subirUnaFoto();
    generarOt(ot -> terminar(ot, estado));

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get(url).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isOk())
        .andExpect(content().bytes(JPEG));
  }

  @Test
  void tecnico_incidenteAbiertoSinOt_403() throws Exception {
    String url = subirUnaFoto();

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnico)))
        .andExpect(status().isForbidden());
    mvc.perform(get(url).with(user(tecnico))).andExpect(status().isForbidden());
  }

  /** Los incidentes sin OT que entran al historial de la instalación (HU-10). */
  @Test
  void tecnico_incidenteEnEsperaDeTercero_veLasFotos() throws Exception {
    String url = subirUnaFoto();
    incidente.ponerEnEsperaDeTercero("UTE", "Corte de energía");
    incidenteRepository.save(incidente);

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get(url).with(user(tecnicoDeOtraCuadrilla))).andExpect(status().isOk());
  }

  @Test
  void tecnico_incidenteCerradoSinOt_veLasFotos() throws Exception {
    String url = subirUnaFoto();
    incidente.ponerEnEsperaDeTercero("Antel", null);
    incidente.cerrarSinOt();
    incidenteRepository.save(incidente);

    mvc.perform(get(FOTOS, incidente.getId()).with(user(tecnicoDeOtraCuadrilla)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get(url).with(user(tecnicoDeOtraCuadrilla))).andExpect(status().isOk());
  }

  @Test
  void jefatura_subeDespuesDelRegistroConLaOtYaAsignada_201() throws Exception {
    UsuarioAutenticado jefatura =
        enSesion(guardarUsuario("Jefatura", "jefatura.fotos@ciemsa.com", Rol.JEFATURA, null));
    asignarOt(cuadrilla);

    subir(jefatura, incidente.getId(), foto("despues.png", PNG))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void incidenteInexistente_404() throws Exception {
    subir(operador, 999999L, foto("frente.jpg", JPEG))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/errores/no-encontrado"));
  }

  @Test
  void leerFoto_tecnicoConIncidenteInexistente_404AntesQue403() throws Exception {
    mvc.perform(get("/api/v1/incidentes/999999/fotos/1").with(user(tecnico)))
        .andExpect(status().isNotFound());
  }

  @Test
  void fotoDeOtroIncidente_404() throws Exception {
    String respuesta =
        subir(operador, incidente.getId(), foto("frente.jpg", JPEG))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Integer idFoto = JsonPath.read(respuesta, "$[0].id");
    Incidente otro = registrarIncidente();

    mvc.perform(get(FOTOS + "/{idFoto}", otro.getId(), idFoto).with(user(operador)))
        .andExpect(status().isNotFound());
  }

  // --- Ayudas ---

  private ResultActions subir(
      UsuarioAutenticado quien, Long idIncidente, MockMultipartFile... fotos) throws Exception {
    var pedido = multipart(FOTOS, idIncidente);
    for (MockMultipartFile foto : fotos) {
      pedido.file(foto);
    }
    return mvc.perform(pedido.with(user(quien)).with(csrf()));
  }

  private void sinFotos() throws Exception {
    mvc.perform(get(FOTOS, incidente.getId()).with(user(operador)))
        .andExpect(jsonPath("$.length()").value(0));
  }

  /** El tipo declarado es siempre image/jpeg: lo que vale es el contenido. */
  private static MockMultipartFile foto(String nombre, byte[] contenido) {
    return new MockMultipartFile("fotos", nombre, "image/jpeg", contenido);
  }

  private Incidente registrarIncidente() {
    return incidenteRepository.save(
        Incidente.registrar(
            idInstalacion,
            OrigenIncidente.MONITOREO,
            idCategoria,
            Prioridad.MEDIA,
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

  private static byte[] bytes(int... valores) {
    byte[] resultado = new byte[valores.length];
    for (int i = 0; i < valores.length; i++) {
      resultado[i] = (byte) valores[i];
    }
    return resultado;
  }

  /** El Operador sube una foto al incidente y devuelve su url. */
  private String subirUnaFoto() throws Exception {
    String respuesta =
        subir(operador, incidente.getId(), foto("frente.jpg", JPEG))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(respuesta, "$[0].url");
  }

  /** Genera la OT del incidente (sin regla de SLA) y la asigna a la cuadrilla para hoy. */
  private void asignarOt(Cuadrilla aCuadrilla) {
    generarOt(ot -> ot.asignar(aCuadrilla.getId(), LocalDate.now(), Instant.now()));
  }

  /** Genera la OT del incidente (sin regla de SLA) y la lleva a su estado con {@code ajuste}. */
  private void generarOt(Consumer<OrdenTrabajo> ajuste) {
    OrdenTrabajo ot = incidente.generarOrdenCorrectiva(Instant.now(), null);
    ajuste.accept(ot);
    incidenteRepository.save(incidente);
    ordenTrabajoRepository.save(ot);
  }

  private static void iniciar(OrdenTrabajo ot, Cuadrilla aCuadrilla) {
    Instant ahora = Instant.now();
    ot.asignar(aCuadrilla.getId(), LocalDate.now(), ahora);
    ot.iniciar(ahora);
  }

  /** Cerrada técnicamente o validada por la cuadrilla del test; anulada, sin asignar. */
  private void terminar(OrdenTrabajo ot, EstadoOT estado) {
    Instant ahora = Instant.now();
    if (estado == EstadoOT.ANULADA) {
      ot.anular("Duplicada", operador.getId(), ahora);
      return;
    }
    iniciar(ot, cuadrilla);
    ot.cerrarTecnicamente(
        new CierreTecnico("Lámpara quemada", "Cambio de lámpara", null), UUID.randomUUID(), ahora);
    if (estado == EstadoOT.VALIDADA) {
      ot.validar(ahora);
    }
  }
}
