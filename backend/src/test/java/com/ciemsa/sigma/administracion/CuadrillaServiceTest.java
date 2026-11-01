package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.administracion.dto.CuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CuadrillaServiceTest {

  @Mock private CuadrillaRepository cuadrillaRepository;
  @Mock private UsuarioRepository usuarioRepository;
  @InjectMocks private CuadrillaService cuadrillaService;

  // --- Alta ---

  @Test
  void crear_datosValidos_guardaNormalizadaYSinTecnicos() {
    CuadrillaResponse respuesta =
        cuadrillaService.crear(new CuadrillaRequest(" Cuadrilla Norte ", "Norte"));

    ArgumentCaptor<Cuadrilla> guardada = ArgumentCaptor.forClass(Cuadrilla.class);
    verify(cuadrillaRepository).saveAndFlush(guardada.capture());
    assertThat(guardada.getValue().getNombre()).isEqualTo("Cuadrilla Norte");
    assertThat(respuesta.activa()).isTrue();
    assertThat(respuesta.tecnicosActivos()).isZero();
  }

  @Test
  void crear_nombreExistente_lanzaConflictoYNoGuarda() {
    when(cuadrillaRepository.existsByNombreIgnoreCase("Norte")).thenReturn(true);

    assertThatThrownBy(() -> cuadrillaService.crear(new CuadrillaRequest("Norte", null)))
        .isInstanceOf(ConflictoException.class)
        .hasMessageContaining("Norte");
    verify(cuadrillaRepository, never()).saveAndFlush(any());
  }

  @Test
  void crear_otraAltaTomoElNombreAlGuardar_lanzaConflicto() {
    when(cuadrillaRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("uq_cuadrilla_nombre"));

    assertThatThrownBy(() -> cuadrillaService.crear(new CuadrillaRequest("Norte", null)))
        .isInstanceOf(ConflictoException.class)
        .extracting("tipo")
        .isEqualTo("cuadrilla-duplicada");
  }

  // --- Modificación ---

  @Test
  void modificar_nombreDeOtraCuadrilla_lanzaConflictoYNoCambia() {
    Cuadrilla norte = Cuadrilla.crear("Norte", "Norte");
    when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(norte));
    when(cuadrillaRepository.existsByNombreIgnoreCaseAndIdNot("Sur", 1L)).thenReturn(true);

    assertThatThrownBy(() -> cuadrillaService.modificar(1L, new CuadrillaRequest("Sur", null)))
        .isInstanceOf(ConflictoException.class);
    assertThat(norte.getNombre()).isEqualTo("Norte");
    verify(cuadrillaRepository, never()).saveAndFlush(any());
  }

  @Test
  void modificar_datosValidos_devuelveLosTecnicosActivos() {
    Cuadrilla norte = Cuadrilla.crear("Norte", "Norte");
    when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(norte));
    when(usuarioRepository.countByCuadrillaIdAndRolAndActivoTrue(1L, Rol.TECNICO)).thenReturn(2L);

    CuadrillaResponse respuesta =
        cuadrillaService.modificar(1L, new CuadrillaRequest("Norte Grande", "Zona Norte"));

    assertThat(respuesta.nombre()).isEqualTo("Norte Grande");
    assertThat(respuesta.zona()).isEqualTo("Zona Norte");
    assertThat(respuesta.tecnicosActivos()).isEqualTo(2);
  }

  @Test
  void modificar_idInexistente_lanzaNoEncontrado() {
    when(cuadrillaRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cuadrillaService.modificar(9L, new CuadrillaRequest("Norte", null)))
        .isInstanceOf(NoEncontradoException.class);
  }

  // --- Desactivar y activar ---

  @Test
  void desactivar_conTecnicosActivos_lanzaReglaNegocioYSigueActiva() {
    Cuadrilla norte = Cuadrilla.crear("Norte", "Norte");
    when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(norte));
    when(usuarioRepository.countByCuadrillaIdAndRolAndActivoTrue(1L, Rol.TECNICO)).thenReturn(2L);

    assertThatThrownBy(() -> cuadrillaService.desactivar(1L))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("2 técnicos activos");
    assertThat(norte.isActiva()).isTrue();
  }

  @Test
  void desactivar_sinTecnicosActivos_quedaInactiva() {
    Cuadrilla norte = Cuadrilla.crear("Norte", "Norte");
    when(cuadrillaRepository.findById(1L)).thenReturn(Optional.of(norte));

    cuadrillaService.desactivar(1L);

    assertThat(norte.isActiva()).isFalse();
  }

  @Test
  void activar_idInexistente_lanzaNoEncontrado() {
    when(cuadrillaRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> cuadrillaService.activar(9L))
        .isInstanceOf(NoEncontradoException.class);
  }
}
