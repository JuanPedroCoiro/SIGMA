package com.ciemsa.sigma.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SesionTest {

  private static final String SESION = "/api/v1/sesion";
  private static final String CORREO = "jefa@ciemsa.com";
  private static final String CONTRASENA = "Prueba.1234";

  @Autowired private WebApplicationContext context;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    usuarioRepository.save(
        Usuario.crear("Jefa", CORREO, passwordEncoder.encode(CONTRASENA), Rol.JEFATURA, null));
  }

  @AfterEach
  void tearDown() {
    usuarioRepository.deleteAll();
  }

  // --- Login (POST) ---

  @Test
  void login_credencialesCorrectas_devuelve204YQuedaAutenticado() throws Exception {
    mvc.perform(post(SESION).param("correo", CORREO).param("contrasena", CONTRASENA).with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(authenticated().withUsername(CORREO).withRoles("JEFATURA"));
  }

  @Test
  void login_correoConMayusculasYEspacios_ingresaIgual() throws Exception {
    mvc.perform(
            post(SESION)
                .param("correo", "  Jefa@CIEMSA.com ")
                .param("contrasena", CONTRASENA)
                .with(csrf()))
        .andExpect(status().isNoContent())
        .andExpect(authenticated().withUsername(CORREO));
  }

  @Test
  void login_contrasenaIncorrecta_devuelve401SinSesion() throws Exception {
    mvc.perform(post(SESION).param("correo", CORREO).param("contrasena", "otra").with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(unauthenticated());
  }

  @Test
  void login_sinTokenCsrf_devuelve403() throws Exception {
    mvc.perform(post(SESION).param("correo", CORREO).param("contrasena", CONTRASENA))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Token CSRF inválido"));
  }

  @Test
  void login_conSesionPrevia_cambiaElIdDeSesion() throws Exception {
    MockHttpSession previa = new MockHttpSession();
    String idPrevio = previa.getId();

    MvcResult resultado =
        mvc.perform(
                post(SESION)
                    .session(previa)
                    .param("correo", CORREO)
                    .param("contrasena", CONTRASENA)
                    .with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(resultado.getRequest().getSession().getId()).isNotEqualTo(idPrevio);
  }

  @Test
  void login_credencialesCorrectas_noGuardaElHashEnLaSesion() throws Exception {
    MockHttpSession sesion = iniciarSesion();

    SecurityContext contexto =
        (SecurityContext)
            sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    UsuarioAutenticado usuario = (UsuarioAutenticado) contexto.getAuthentication().getPrincipal();

    assertThat(usuario.getPassword()).isNull();
  }

  // --- Quién soy (GET) ---

  @Test
  void sesion_despuesDelLogin_devuelveElUsuarioSinElHash() throws Exception {
    MockHttpSession sesion = iniciarSesion();

    mvc.perform(get(SESION).session(sesion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.nombre").value("Jefa"))
        .andExpect(jsonPath("$.correo").value(CORREO))
        .andExpect(jsonPath("$.rol").value("JEFATURA"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void sesion_sinLogin_devuelve401() throws Exception {
    mvc.perform(get(SESION))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("No autenticado"));
  }

  // --- Logout (DELETE) ---

  @Test
  void logout_conSesion_devuelve204EInvalidaLaSesion() throws Exception {
    MockHttpSession sesion = iniciarSesion();

    mvc.perform(delete(SESION).session(sesion).with(csrf())).andExpect(status().isNoContent());

    assertThat(sesion.isInvalid()).isTrue();
  }

  /** Hace login y devuelve la sesión creada, como la cookie que guardaría el navegador. */
  private MockHttpSession iniciarSesion() throws Exception {
    MvcResult resultado =
        mvc.perform(
                post(SESION).param("correo", CORREO).param("contrasena", CONTRASENA).with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();
    return (MockHttpSession) resultado.getRequest().getSession(false);
  }
}
