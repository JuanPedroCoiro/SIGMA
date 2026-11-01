package com.ciemsa.sigma.administracion;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CuadrillaControllerTest {

  private static final String CUADRILLAS = "/api/v1/cuadrillas";
  private static final String HASH = "$2a$10$hashDePrueba";

  @Autowired private WebApplicationContext context;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  private MockMvc mvc;
  private UsuarioAutenticado jefa;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    jefa = enSesion(guardarUsuario("Jefa", "jefa@ciemsa.com", Rol.JEFATURA, null));
  }

  @AfterEach
  void tearDown() {
    usuarioRepository.deleteAll();
    cuadrillaRepository.deleteAll();
  }

  // --- Listado ---

  @Test
  void listar_cuentaSoloLosTecnicosActivosDeCadaCuadrilla() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    Cuadrilla sur = guardarCuadrilla("Sur");
    guardarUsuario("Juan", "juan@ciemsa.com", Rol.TECNICO, norte);
    guardarUsuario("Pedro", "pedro@ciemsa.com", Rol.TECNICO, norte);
    Usuario inactivo = guardarUsuario("Luis", "luis@ciemsa.com", Rol.TECNICO, norte);
    inactivo.darDeBaja();
    usuarioRepository.save(inactivo);

    mvc.perform(get(CUADRILLAS).with(user(jefa)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].nombre").value(norte.getNombre()))
        .andExpect(jsonPath("$[0].tecnicosActivos").value(2))
        .andExpect(jsonPath("$[1].nombre").value(sur.getNombre()))
        .andExpect(jsonPath("$[1].tecnicosActivos").value(0));
  }

  // --- Alta ---

  @Test
  void crear_datosValidos_devuelve201ConLocation() throws Exception {
    enviar(post(CUADRILLAS), "{\"nombre\": \" Cuadrilla Oeste \", \"zona\": \"Oeste\"}")
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/cuadrillas/")))
        .andExpect(jsonPath("$.nombre").value("Cuadrilla Oeste"))
        .andExpect(jsonPath("$.zona").value("Oeste"))
        .andExpect(jsonPath("$.activa").value(true))
        .andExpect(jsonPath("$.tecnicosActivos").value(0));
  }

  @Test
  void crear_sinNombre_devuelve400ConElCampo() throws Exception {
    enviar(post(CUADRILLAS), "{\"nombre\": \"  \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")));
  }

  @Test
  void crear_nombreExistenteConOtrasMayusculas_devuelve409() throws Exception {
    guardarCuadrilla("Norte");

    enviar(post(CUADRILLAS), "{\"nombre\": \"NORTE\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errores/cuadrilla-duplicada"));
  }

  // --- Modificación ---

  @Test
  void modificar_datosValidos_devuelve200() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");

    enviar(put(CUADRILLAS + "/" + norte.getId()), "{\"nombre\": \"Norte\", \"zona\": \"Centro\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.zona").value("Centro"));
  }

  @Test
  void modificar_conElNombreDeOtra_devuelve409() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    guardarCuadrilla("Sur");

    enviar(put(CUADRILLAS + "/" + norte.getId()), "{\"nombre\": \"Sur\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errores/cuadrilla-duplicada"));
  }

  @Test
  void modificar_idInexistente_devuelve404() throws Exception {
    enviar(put(CUADRILLAS + "/999999"), "{\"nombre\": \"Norte\"}").andExpect(status().isNotFound());
  }

  // --- Desactivar y activar ---

  @Test
  void desactivar_sinTecnicosActivos_devuelve204YQuedaInactiva() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    Usuario inactivo = guardarUsuario("Luis", "luis@ciemsa.com", Rol.TECNICO, norte);
    inactivo.darDeBaja();
    usuarioRepository.save(inactivo);

    accion(norte, "desactivar").andExpect(status().isNoContent());

    mvc.perform(get(CUADRILLAS).with(user(jefa))).andExpect(jsonPath("$[0].activa").value(false));
  }

  @Test
  void desactivar_conTecnicosActivos_devuelve400ConLaCantidad() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    Cuadrilla sur = guardarCuadrilla("Sur");
    guardarUsuario("Juan", "juan@ciemsa.com", Rol.TECNICO, norte);
    guardarUsuario("Pedro", "pedro@ciemsa.com", Rol.TECNICO, norte);
    guardarUsuario("Mario", "mario@ciemsa.com", Rol.TECNICO, sur);
    Usuario inactivo = guardarUsuario("Luis", "luis@ciemsa.com", Rol.TECNICO, norte);
    inactivo.darDeBaja();
    usuarioRepository.save(inactivo);

    accion(norte, "desactivar")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"))
        .andExpect(jsonPath("$.detail", containsString("2 técnicos activos")));
  }

  @Test
  void activar_cuadrillaInactiva_devuelve204() throws Exception {
    Cuadrilla norte = Cuadrilla.crear("Norte", "Norte");
    norte.desactivar(0);
    cuadrillaRepository.save(norte);

    accion(norte, "activar").andExpect(status().isNoContent());

    mvc.perform(get(CUADRILLAS).with(user(jefa))).andExpect(jsonPath("$[0].activa").value(true));
  }

  // --- Seguridad: solo Jefatura ---

  @Test
  void crear_conRolOperador_devuelve403() throws Exception {
    UsuarioAutenticado operador =
        enSesion(guardarUsuario("Ana", "ana@ciemsa.com", Rol.OPERADOR, null));

    enviarComo(operador, post(CUADRILLAS), "{\"nombre\": \"Oeste\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
  }

  @Test
  void crear_conRolTecnico_devuelve403() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    UsuarioAutenticado tecnico =
        enSesion(guardarUsuario("Juan", "juan@ciemsa.com", Rol.TECNICO, norte));

    enviarComo(tecnico, post(CUADRILLAS), "{\"nombre\": \"Oeste\"}")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
  }

  @Test
  void desactivar_conRolOperador_devuelve403() throws Exception {
    Cuadrilla norte = guardarCuadrilla("Norte");
    UsuarioAutenticado operador =
        enSesion(guardarUsuario("Ana", "ana@ciemsa.com", Rol.OPERADOR, null));

    mvc.perform(
            post(CUADRILLAS + "/" + norte.getId() + "/desactivar")
                .with(user(operador))
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  private ResultActions enviar(MockHttpServletRequestBuilder pedido, String json) throws Exception {
    return enviarComo(jefa, pedido, json);
  }

  private ResultActions enviarComo(
      UsuarioAutenticado quien, MockHttpServletRequestBuilder pedido, String json)
      throws Exception {
    return mvc.perform(
        pedido
            .with(user(quien))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions accion(Cuadrilla cuadrilla, String accion) throws Exception {
    return mvc.perform(
        post(CUADRILLAS + "/" + cuadrilla.getId() + "/" + accion).with(user(jefa)).with(csrf()));
  }

  private Cuadrilla guardarCuadrilla(String nombre) {
    return cuadrillaRepository.save(Cuadrilla.crear(nombre, nombre));
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
