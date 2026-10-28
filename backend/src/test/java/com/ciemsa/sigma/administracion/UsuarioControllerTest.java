package com.ciemsa.sigma.administracion;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UsuarioControllerTest {

  private static final String USUARIOS = "/api/v1/usuarios";
  private static final String CONTRASENA = "Prueba.1234";

  @Autowired private WebApplicationContext context;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  private MockMvc mvc;
  private UsuarioAutenticado jefa;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    jefa = enSesion(guardar("Jefa", "jefa@ciemsa.com", Rol.JEFATURA));
  }

  @AfterEach
  void tearDown() {
    usuarioRepository.deleteAll();
    cuadrillaRepository.deleteAll();
  }

  // --- Seguridad: solo Jefatura, en cada pedido ---

  @Test
  void listar_conRolOperador_devuelve403() throws Exception {
    UsuarioAutenticado operador = enSesion(guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR));

    mvc.perform(get(USUARIOS).with(user(operador)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Acceso denegado"));
  }

  // --- Alta (CU-13a) ---

  @Test
  void crear_datosValidos_devuelve201ConLocationYSinElHash() throws Exception {
    crear(
            """
      {"nombre": "Ana", "correo": "Ana@Ciemsa.com", "password": "Prueba.1234",
       "rol": "OPERADOR"}
      """)
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", startsWith("/api/v1/usuarios/")))
        .andExpect(jsonPath("$.correo").value("ana@ciemsa.com"))
        .andExpect(jsonPath("$.activo").value(true))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist());
  }

  @Test
  void crear_datosInvalidos_devuelve400ConCadaCampo() throws Exception {
    crear(
            """
      {"nombre": "", "correo": "no-es-un-correo", "password": "corta"}
      """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/validacion"))
        .andExpect(
            jsonPath(
                "$.errores[*].campo", containsInAnyOrder("nombre", "correo", "password", "rol")));
  }

  @Test
  void crear_correoExistente_devuelve409() throws Exception {
    crear(
            """
      {"nombre": "Otra", "correo": "jefa@ciemsa.com", "password": "Prueba.1234",
       "rol": "OPERADOR"}
      """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("/errores/correo-duplicado"));
  }

  @Test
  void crear_tecnicoSinCuadrilla_devuelve400() throws Exception {
    crear(
            """
      {"nombre": "Juan", "correo": "juan@ciemsa.com", "password": "Prueba.1234",
       "rol": "TECNICO"}
      """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"))
        .andExpect(jsonPath("$.detail", containsString("cuadrilla")));
  }

  @Test
  void crear_tecnicoConCuadrilla_devuelveLaCuadrilla() throws Exception {
    Cuadrilla norte = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Norte", "Norte"));

    crear(
            """
      {"nombre": "Juan", "correo": "juan@ciemsa.com", "password": "Prueba.1234",
       "rol": "TECNICO", "idCuadrilla": %d}
      """
                .formatted(norte.getId()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombreCuadrilla").value("Cuadrilla Norte"));
  }

  // --- Consulta (CU-13c) ---

  @Test
  void listar_conJefatura_devuelvePaginaOrdenadaPorNombre() throws Exception {
    guardar("Beto", "beto@ciemsa.com", Rol.OPERADOR);
    guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR);

    mvc.perform(get(USUARIOS).with(user(jefa)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].nombre").value("Ana"))
        .andExpect(jsonPath("$.contenido[1].nombre").value("Beto"))
        .andExpect(jsonPath("$.contenido[2].nombre").value("Jefa"))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamanio").value(20))
        .andExpect(jsonPath("$.totalElementos").value(3))
        .andExpect(jsonPath("$.totalPaginas").value(1));
  }

  @Test
  void listar_filtrandoPorRolYEstado_devuelveSoloLosQueCoinciden() throws Exception {
    guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR);
    Usuario beto = guardar("Beto", "beto@ciemsa.com", Rol.OPERADOR);
    beto.darDeBaja();
    usuarioRepository.save(beto);

    mvc.perform(get(USUARIOS).param("rol", "OPERADOR").param("activo", "true").with(user(jefa)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(1))
        .andExpect(jsonPath("$.contenido[0].nombre").value("Ana"));
  }

  @Test
  void obtener_idInexistente_devuelve404() throws Exception {
    mvc.perform(get(USUARIOS + "/999999").with(user(jefa))).andExpect(status().isNotFound());
  }

  // --- Contraseña, desactivar y activar (CU-13b, CU-13d) ---

  @Test
  void restablecerPassword_luegoIngresaConLaNueva() throws Exception {
    Usuario ana = guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR);

    mvc.perform(
            post(USUARIOS + "/" + ana.getId() + "/restablecer-password")
                .with(user(jefa))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"Nueva.5678\"}"))
        .andExpect(status().isNoContent());

    ingresar("ana@ciemsa.com", "Nueva.5678").andExpect(status().isNoContent());
  }

  @Test
  void desactivar_operador_devuelve204YYaNoPuedeIngresar() throws Exception {
    Usuario ana = guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR);

    mvc.perform(post(USUARIOS + "/" + ana.getId() + "/desactivar").with(user(jefa)).with(csrf()))
        .andExpect(status().isNoContent());

    ingresar("ana@ciemsa.com", CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/cuenta-desactivada"));
  }

  @Test
  void desactivar_aSiMismo_devuelve400() throws Exception {
    mvc.perform(post(USUARIOS + "/" + jefa.getId() + "/desactivar").with(user(jefa)).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/errores/regla-negocio"));
  }

  @Test
  void activar_usuarioDesactivado_vuelveAPoderIngresar() throws Exception {
    Usuario ana = guardar("Ana", "ana@ciemsa.com", Rol.OPERADOR);
    ana.darDeBaja();
    usuarioRepository.save(ana);

    mvc.perform(post(USUARIOS + "/" + ana.getId() + "/activar").with(user(jefa)).with(csrf()))
        .andExpect(status().isNoContent());

    ingresar("ana@ciemsa.com", CONTRASENA).andExpect(status().isNoContent());
  }

  // --- Cuadrillas (desplegable) ---

  @Test
  void cuadrillas_conSesion_devuelveSoloLasActivasOrdenadas() throws Exception {
    cuadrillaRepository.save(Cuadrilla.crear("Sur", "Sur"));
    cuadrillaRepository.save(Cuadrilla.crear("Norte", "Norte"));
    Cuadrilla este = Cuadrilla.crear("Este", "Este");
    ReflectionTestUtils.setField(este, "activa", false);
    cuadrillaRepository.save(este);

    mvc.perform(get("/api/v1/cuadrillas").with(user(jefa)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].nombre").value("Norte"))
        .andExpect(jsonPath("$[1].nombre").value("Sur"));
  }

  private ResultActions crear(String json) throws Exception {
    return mvc.perform(
        post(USUARIOS)
            .with(user(jefa))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions ingresar(String correo, String contrasena) throws Exception {
    return mvc.perform(
        post("/api/v1/sesion")
            .param("correo", correo)
            .param("contrasena", contrasena)
            .with(csrf()));
  }

  private Usuario guardar(String nombre, String correo, Rol rol) {
    return usuarioRepository.save(
        Usuario.crear(nombre, correo, passwordEncoder.encode(CONTRASENA), rol, null));
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
