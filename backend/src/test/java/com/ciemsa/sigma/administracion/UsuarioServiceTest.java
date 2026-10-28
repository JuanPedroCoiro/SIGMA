package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.administracion.dto.CrearUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.ModificarUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.UsuarioResponse;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

  private static final String HASH = "$2a$10$hashDePrueba";

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private CuadrillaRepository cuadrillaRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UsuarioActual usuarioActual;
  @InjectMocks private UsuarioService usuarioService;

  // --- Alta (CU-13a) ---

  @Test
  void crear_datosValidos_guardaConElCorreoNormalizadoYLaContrasenaHasheada() {
    when(passwordEncoder.encode("Prueba.1234")).thenReturn(HASH);

    UsuarioResponse respuesta =
        usuarioService.crear(
            new CrearUsuarioRequest("Ana", " Ana@Ciemsa.com", "Prueba.1234", Rol.OPERADOR, null));

    ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioRepository).saveAndFlush(guardado.capture());
    assertThat(guardado.getValue().getCorreo()).isEqualTo("ana@ciemsa.com");
    assertThat(guardado.getValue().getPasswordHash()).isEqualTo(HASH);
    assertThat(respuesta.correo()).isEqualTo("ana@ciemsa.com");
  }

  @Test
  void crear_correoExistente_lanzaConflictoYNoGuarda() {
    when(usuarioRepository.existsByCorreo("ana@ciemsa.com")).thenReturn(true);

    assertThatThrownBy(() -> usuarioService.crear(altaOperador("Ana@ciemsa.com")))
        .isInstanceOf(ConflictoException.class)
        .hasMessageContaining("ana@ciemsa.com");
    verify(usuarioRepository, never()).saveAndFlush(any());
  }

  @Test
  void crear_tecnicoConCuadrillaInexistente_lanzaReglaNegocio() {
    when(cuadrillaRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                usuarioService.crear(
                    new CrearUsuarioRequest(
                        "Juan", "juan@ciemsa.com", "Prueba.1234", Rol.TECNICO, 9L)))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("cuadrilla");
  }

  @Test
  void crear_otraAltaTomoElCorreoAlGuardar_lanzaConflicto() {
    when(passwordEncoder.encode("Prueba.1234")).thenReturn(HASH);
    when(usuarioRepository.saveAndFlush(any(Usuario.class)))
        .thenThrow(new DataIntegrityViolationException("uq_usuario_correo"));

    assertThatThrownBy(() -> usuarioService.crear(altaOperador("ana@ciemsa.com")))
        .isInstanceOf(ConflictoException.class);
  }

  // --- Modificación (CU-13b) ---

  @Test
  void modificar_correoDeOtraCuenta_lanzaConflicto() {
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(operador("ana@ciemsa.com")));
    when(usuarioRepository.existsByCorreoAndIdNot("beto@ciemsa.com", 1L)).thenReturn(true);

    assertThatThrownBy(
            () ->
                usuarioService.modificar(
                    1L, new ModificarUsuarioRequest("Ana", "beto@ciemsa.com", Rol.OPERADOR, null)))
        .isInstanceOf(ConflictoException.class);
  }

  @Test
  void modificar_unicaJefaturaPasaAOperador_lanzaReglaNegocio() {
    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(jefatura("jefa@ciemsa.com")));
    when(usuarioRepository.countByRolAndActivoTrue(Rol.JEFATURA)).thenReturn(1L);

    assertThatThrownBy(
            () ->
                usuarioService.modificar(
                    1L, new ModificarUsuarioRequest("Jefa", "jefa@ciemsa.com", Rol.OPERADOR, null)))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("Jefatura");
  }

  // --- Consulta y baja (CU-13c, CU-13d) ---

  @Test
  void obtener_idInexistente_lanzaNoEncontrado() {
    when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> usuarioService.obtener(99L)).isInstanceOf(NoEncontradoException.class);
  }

  @Test
  void darDeBaja_aSiMismo_lanzaReglaNegocioSinBuscarlo() {
    when(usuarioActual.id()).thenReturn(1L);

    assertThatThrownBy(() -> usuarioService.darDeBaja(1L))
        .isInstanceOf(ReglaNegocioException.class);
    verify(usuarioRepository, never()).findById(any());
  }

  @Test
  void darDeBaja_unicaJefaturaActiva_lanzaReglaNegocio() {
    when(usuarioActual.id()).thenReturn(1L);
    when(usuarioRepository.findById(2L)).thenReturn(Optional.of(jefatura("otra@ciemsa.com")));
    when(usuarioRepository.countByRolAndActivoTrue(Rol.JEFATURA)).thenReturn(1L);

    assertThatThrownBy(() -> usuarioService.darDeBaja(2L))
        .isInstanceOf(ReglaNegocioException.class);
  }

  @Test
  void darDeBaja_operador_quedaInactivo() {
    Usuario operador = operador("ana@ciemsa.com");
    when(usuarioActual.id()).thenReturn(1L);
    when(usuarioRepository.findById(2L)).thenReturn(Optional.of(operador));

    usuarioService.darDeBaja(2L);

    assertThat(operador.isActivo()).isFalse();
  }

  private static CrearUsuarioRequest altaOperador(String correo) {
    return new CrearUsuarioRequest("Ana", correo, "Prueba.1234", Rol.OPERADOR, null);
  }

  private static Usuario operador(String correo) {
    return Usuario.crear("Ana", correo, HASH, Rol.OPERADOR, null);
  }

  private static Usuario jefatura(String correo) {
    return Usuario.crear("Jefa", correo, HASH, Rol.JEFATURA, null);
  }
}
