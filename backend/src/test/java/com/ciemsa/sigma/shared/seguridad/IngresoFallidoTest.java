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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Flujos alternativos de CU-12: 4a, 4b, 4c y *a, y lo que dejan en el log técnico (#95). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class IngresoFallidoTest {

  private static final String SESION = "/api/v1/sesion";
  private static final String CORREO = "tecnico@ciemsa.com";
  private static final String CONTRASENA = "Prueba.1234";
  private static final String INCORRECTA = "otra";

  /** Una contraseña que no puede aparecer en el log por casualidad, como «otra». */
  private static final String INCORRECTA_RARA = "Clave.Que.No.Va.987";

  private static final String AVISO_BLOQUEO = "por intentos fallidos";

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
        .andExpect(jsonPath("$.type").value("/errores/credenciales"))
        .andExpect(unauthenticated());
  }

  @Test
  void login_correoInexistente_respondeIgualQueContrasenaIncorrecta() throws Exception {
    ingresar("nadie@ciemsa.com", CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/credenciales"))
        .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
  }

  @Test
  void login_cuentaDadaDeBaja_devuelveCuentaDesactivada() throws Exception {
    Usuario usuario = usuarioRepository.findByCorreo(CORREO).orElseThrow();
    usuario.darDeBaja();
    usuarioRepository.save(usuario);

    ingresar(CORREO, CONTRASENA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/cuenta-desactivada"))
        .andExpect(unauthenticated());
  }

  @Test
  void login_cuentaDadaDeBajaConContrasenaIncorrecta_noRevelaQueEstaDesactivada() throws Exception {
    Usuario usuario = usuarioRepository.findByCorreo(CORREO).orElseThrow();
    usuario.darDeBaja();
    usuarioRepository.save(usuario);

    ingresar(CORREO, INCORRECTA)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/credenciales"));
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

    Instant bloqueadoHasta = usuario().getBloqueadoHasta();
    assertThat(bloqueadoHasta)
        .isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(1, ChronoUnit.MINUTES));
    ingresar(CORREO, CONTRASENA)
        .andExpect(status().isLocked())
        .andExpect(jsonPath("$.type").value("/errores/usuario-bloqueado"))
        .andExpect(
            jsonPath("$.bloqueadoHasta")
                .value(bloqueadoHasta.truncatedTo(ChronoUnit.SECONDS).toString()))
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

  // --- Log técnico (#95): el bloqueo queda como WARN, sin datos sensibles ---

  @Test
  void bloqueo_quedaUnaSolaVezEnElLogConElIdYSinContrasenaNiCorreo(CapturedOutput salida)
      throws Exception {
    for (int i = 0; i < 5; i++) {
      ingresar(CORREO, INCORRECTA_RARA).andExpect(status().isUnauthorized());
    }
    // Con la cuenta ya bloqueada, otro intento no repite el aviso.
    ingresar(CORREO, INCORRECTA_RARA).andExpect(status().isLocked());

    String log = salida.getOut();
    assertThat(log)
        .contains("WARN")
        .contains("Usuario " + usuario().getId() + " bloqueado hasta")
        .doesNotContain(INCORRECTA_RARA)
        .doesNotContain(CORREO);
    assertThat(log.split(AVISO_BLOQUEO, -1)).hasSize(2);
  }

  @Test
  void correoInexistente_quedaEnElLogSinElCorreo(CapturedOutput salida) throws Exception {
    ingresar("nadie@ciemsa.com", INCORRECTA_RARA).andExpect(status().isUnauthorized());

    assertThat(salida.getOut())
        .contains("Intento de ingreso con un correo que no está registrado")
        .doesNotContain("nadie@ciemsa.com")
        .doesNotContain(INCORRECTA_RARA);
  }

  // --- *a: sesión expirada ---

  @Test
  void sesion_vencidaOInexistente_devuelveSinSesion() throws Exception {
    mvc.perform(get(SESION).session(new MockHttpSession()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
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
