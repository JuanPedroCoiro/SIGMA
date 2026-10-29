package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioTest {

  private static final Instant AHORA = Instant.parse("2026-10-22T13:00:00Z");
  private static final Duration BLOQUEO = Duration.ofMinutes(15);
  private static final String HASH = "$2a$10$hashDePrueba";

  // --- Alta (CU-13a) ---

  @Test
  void crear_correoConMayusculasYEspacios_loGuardaNormalizado() {
    Usuario usuario = Usuario.crear("Ana", "  Ana.Perez@Ciemsa.COM ", HASH, Rol.OPERADOR, null);

    assertThat(usuario.getCorreo()).isEqualTo("ana.perez@ciemsa.com");
  }

  @Test
  void crear_usuarioNuevo_quedaActivoYSinIntentos() {
    Usuario usuario = operador();

    assertThat(usuario.isActivo()).isTrue();
    assertThat(usuario.getIntentosFallidos()).isZero();
    assertThat(usuario.getBloqueadoHasta()).isNull();
  }

  @Test
  void crear_tecnicoSinCuadrilla_lanzaReglaNegocio() {
    assertThatThrownBy(() -> Usuario.crear("Juan", "juan@ciemsa.com", HASH, Rol.TECNICO, null))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("cuadrilla");
  }

  @Test
  void crear_tecnicoConCuadrillaInactiva_lanzaReglaNegocio() {
    Cuadrilla inactiva = Cuadrilla.crear("Cuadrilla Norte", "Norte");
    ReflectionTestUtils.setField(inactiva, "activa", false);

    assertThatThrownBy(() -> Usuario.crear("Juan", "juan@ciemsa.com", HASH, Rol.TECNICO, inactiva))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("inactiva");
  }

  @Test
  void crear_operadorConCuadrilla_ignoraLaCuadrilla() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Cuadrilla Norte", "Norte");

    Usuario usuario = Usuario.crear("Ana", "ana@ciemsa.com", HASH, Rol.OPERADOR, cuadrilla);

    assertThat(usuario.getCuadrilla()).isNull();
  }

  // --- Modificación y baja (CU-13b, CU-13d) ---

  @Test
  void modificar_deTecnicoAOperador_limpiaLaCuadrilla() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Cuadrilla Norte", "Norte");
    Usuario usuario = Usuario.crear("Juan", "juan@ciemsa.com", HASH, Rol.TECNICO, cuadrilla);

    usuario.modificar("Juan", "juan@ciemsa.com", Rol.OPERADOR, null);

    assertThat(usuario.getRol()).isEqualTo(Rol.OPERADOR);
    assertThat(usuario.getCuadrilla()).isNull();
  }

  @Test
  void darDeBaja_usuarioActivo_quedaInactivo() {
    Usuario usuario = operador();

    usuario.darDeBaja();

    assertThat(usuario.isActivo()).isFalse();
  }

  @Test
  void reactivar_usuarioDadoDeBaja_quedaActivo() {
    Usuario usuario = operador();
    usuario.darDeBaja();

    usuario.reactivar();

    assertThat(usuario.isActivo()).isTrue();
  }

  @Test
  void restablecerContrasena_cuentaBloqueada_cambiaElHashYLaDesbloquea() {
    Usuario usuario = operador();
    fallar(usuario, 5, AHORA);

    usuario.restablecerContrasena("$2a$10$otroHash");

    assertThat(usuario.getPasswordHash()).isEqualTo("$2a$10$otroHash");
    assertThat(usuario.estaBloqueado(AHORA)).isFalse();
  }

  // --- Bloqueo por intentos fallidos (CU-12 4c) ---

  @Test
  void registrarIntentoFallido_cuatroVeces_noBloquea() {
    Usuario usuario = operador();

    fallar(usuario, 4, AHORA);

    assertThat(usuario.getIntentosFallidos()).isEqualTo(4);
    assertThat(usuario.estaBloqueado(AHORA)).isFalse();
  }

  @Test
  void registrarIntentoFallido_quintaVez_bloqueaDuranteLaDuracion() {
    Usuario usuario = operador();

    fallar(usuario, 5, AHORA);

    assertThat(usuario.getBloqueadoHasta()).isEqualTo(AHORA.plus(BLOQUEO));
    assertThat(usuario.estaBloqueado(AHORA.plus(Duration.ofMinutes(14)))).isTrue();
  }

  @Test
  void estaBloqueado_cuandoVenceElBloqueo_devuelveFalse() {
    Usuario usuario = operador();
    fallar(usuario, 5, AHORA);

    assertThat(usuario.estaBloqueado(AHORA.plus(BLOQUEO))).isFalse();
  }

  @Test
  void registrarIntentoFallido_mientrasEstaBloqueado_noExtiendeElBloqueo() {
    Usuario usuario = operador();
    fallar(usuario, 5, AHORA);

    fallar(usuario, 5, AHORA.plus(Duration.ofMinutes(5)));

    assertThat(usuario.getBloqueadoHasta()).isEqualTo(AHORA.plus(BLOQUEO));
    assertThat(usuario.getIntentosFallidos()).isZero();
  }

  @Test
  void registrarIngresoExitoso_conIntentosPrevios_reiniciaElContador() {
    Usuario usuario = operador();
    fallar(usuario, 4, AHORA);

    usuario.registrarIngresoExitoso();
    fallar(usuario, 1, AHORA);

    assertThat(usuario.getIntentosFallidos()).isEqualTo(1);
    assertThat(usuario.estaBloqueado(AHORA)).isFalse();
  }

  private static Usuario operador() {
    return Usuario.crear("Ana", "ana@ciemsa.com", HASH, Rol.OPERADOR, null);
  }

  private static void fallar(Usuario usuario, int veces, Instant cuando) {
    for (int i = 0; i < veces; i++) {
      usuario.registrarIntentoFallido(cuando, BLOQUEO);
    }
  }
}
