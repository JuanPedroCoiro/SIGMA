package com.ciemsa.sigma.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Flujos alternativos de CU-12: 4a, 4b, 4c y *a. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IngresoFallidoTest {

  private static final String SESION = "/api/v1/sesion";
  private static final String CORREO = "tecnico@ciemsa.com";
  private static final String CONTRASENA = "Prueba.1234";
  private static final String INCORRECTA = "otra";

  @Autowired private WebApplicationContext context;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    usuarioRepository.save(
        Usuario.crear("Operador", CORREO, passwordEncoder.encode(CONTRASENA), Rol.OPERADOR, null));
  }

  @AfterEach
  void tearDown() {
    usuarioRepository.deleteAll();
  }

  // --- 4a: credenciales incorrectas ---

  @Test
  void login_contrasenaIncorrecta_devuelveCredencialesIncorrectas() throws Exception {
    ingresar(CORREO, INCORRECTA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INCORRECTAS"))
        .andExpect(unauthenticated());
  }

  @Test
  void login_correoInexistente_respondeIgualQueContrasenaIncorrecta() throws Exception {
    ingresar("nadie@ciemsa.com", CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INCORRECTAS"))
        .andExpect(jsonPath("$.detail").value("El correo o la contraseña no son correctos."));
  }

  // --- 4b: cuenta desactivada ---

  @Test
  void login_cuentaDadaDeBaja_devuelveCuentaDesactivada() throws Exception {
    Usuario usuario = usuarioRepository.findByCorreo(CORREO).orElseThrow();
    usuario.darDeBaja();
    usuarioRepository.save(usuario);

    ingresar(CORREO, CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CUENTA_DESACTIVADA"))
        .andExpect(
            jsonPath("$.detail").value("Tu cuenta está desactivada. Contactá a la Jefatura."))
        .andExpect(unauthenticated());
  }

  // --- 4c: bloqueo por intentos fallidos ---

  @Test
  void login_cuatroFallosYLuegoCorrecto_ingresaYReiniciaElContador() throws Exception {
    fallar(4);
    assertThat(usuario().getIntentosFallidos()).isEqualTo(4);

    ingresar(CORREO, CONTRASENA).andExpect(status().isNoContent());

    assertThat(usuario().getIntentosFallidos()).isZero();
  }

  @Test
  void login_quintoFallo_bloqueaLaCuentaAunConLaContrasenaCorrecta() throws Exception {
    fallar(5);

    assertThat(usuario().getBloqueadoHasta())
        .isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(1, ChronoUnit.MINUTES));
    ingresar(CORREO, CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"))
        .andExpect(unauthenticated());
  }

  @Test
  void login_bloqueoVencido_ingresaYLimpiaElBloqueo() throws Exception {
    Usuario usuario = usuario();
    ReflectionTestUtils.setField(usuario, "bloqueadoHasta", Instant.now().minusSeconds(60));
    usuarioRepository.save(usuario);

    ingresar(CORREO, CONTRASENA).andExpect(status().isNoContent());

    assertThat(usuario().getBloqueadoHasta()).isNull();
  }

  // --- *a: sesión expirada ---

  @Test
  void sesion_vencidaOInexistente_devuelveSesionRequerida() throws Exception {
    mvc.perform(get(SESION).session(new MockHttpSession()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("SESION_REQUERIDA"));
  }

  private ResultActions ingresar(String correo, String contrasena) throws Exception {
    return mvc.perform(
        post(SESION).param("correo", correo).param("contrasena", contrasena).with(csrf()));
  }

  private void fallar(int veces) throws Exception {
    for (int i = 0; i < veces; i++) {
      ingresar(CORREO, INCORRECTA).andExpect(status().isUnauthorized());
    }
  }

  /** Relee el usuario de la base para ver lo que guardó el listener. */
  private Usuario usuario() {
    return usuarioRepository.findByCorreo(CORREO).orElseThrow();
  }
}
