package com.ciemsa.sigma.ot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.EstadoOrdenCambiado;
import com.ciemsa.sigma.ot.domain.OrdenAsignada;
import com.ciemsa.sigma.ot.domain.OrdenLiberada;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.TransicionInvalidaException;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TomaOtServiceTest {

  /** 9/11 a las 23:30 en Montevideo; en UTC ya es el 10/11. */
  private static final Instant AHORA = Instant.parse("2026-11-10T02:30:00Z");

  private static final LocalDate HOY_MONTEVIDEO = LocalDate.parse("2026-11-09");
  private static final Long ID_TECNICO = 5L;
  private static final Long NORTE = 7L;
  private static final Long SUR = 8L;

  @Mock private OrdenTrabajoRepository ordenTrabajoRepository;
  @Mock private AccesoOt accesoOt;
  @Mock private UsuarioService usuarioService;
  @Mock private UsuarioActual usuarioActual;
  @Mock private ApplicationEventPublisher eventos;
  @Mock private ArmadorDetalleOt armadorDetalleOt;
  private TomaOtService servicio;

  @BeforeEach
  void setUp() {
    lenient().when(usuarioActual.id()).thenReturn(ID_TECNICO);
    servicio =
        new TomaOtService(
            ordenTrabajoRepository,
            accesoOt,
            usuarioService,
            usuarioActual,
            Clock.fixed(AHORA, ZoneOffset.UTC),
            ZoneId.of("America/Montevideo"),
            eventos,
            armadorDetalleOt);
  }

  // --- Tomar ---

  @Test
  void tomar_pendiente_quedaAsignadaASuCuadrillaConLaFechaDeHoyEnMontevideo() {
    OrdenTrabajo ot = pendiente();
    when(usuarioService.idCuadrillaDe(ID_TECNICO)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findConBloqueoById(1L)).thenReturn(Optional.of(ot));

    servicio.tomar(1L);

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
    assertThat(ot.getIdCuadrilla()).isEqualTo(NORTE);
    assertThat(ot.getFechaEjecucion()).isEqualTo(HOY_MONTEVIDEO);
    verify(ordenTrabajoRepository).save(ot);
    verify(eventos)
        .publishEvent(
            new EstadoOrdenCambiado(1L, EstadoOT.PENDIENTE, EstadoOT.ASIGNADA, ID_TECNICO, AHORA));
    verify(eventos)
        .publishEvent(new OrdenAsignada(1L, null, NORTE, null, HOY_MONTEVIDEO, ID_TECNICO, AHORA));
  }

  @Test
  void tomar_yaAsignadaAOtraCuadrilla_seRechazaSinQuitarsela() {
    OrdenTrabajo ot = pendiente();
    ot.asignar(SUR, HOY_MONTEVIDEO, AHORA.minusSeconds(60));
    ot.extraerEventos(ID_TECNICO);
    when(usuarioService.idCuadrillaDe(ID_TECNICO)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findConBloqueoById(1L)).thenReturn(Optional.of(ot));

    assertThatThrownBy(() -> servicio.tomar(1L)).isInstanceOf(TransicionInvalidaException.class);

    assertThat(ot.getIdCuadrilla()).isEqualTo(SUR);
    verify(ordenTrabajoRepository, never()).save(any());
    verifyNoInteractions(eventos);
  }

  @Test
  void tomar_tecnicoSinCuadrilla_seRechazaSinBuscarLaOt() {
    when(usuarioService.idCuadrillaDe(ID_TECNICO)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.tomar(1L)).isInstanceOf(ReglaNegocioException.class);

    verifyNoInteractions(ordenTrabajoRepository, eventos);
  }

  @Test
  void tomar_otInexistente_lanzaNoEncontrado() {
    when(usuarioService.idCuadrillaDe(ID_TECNICO)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findConBloqueoById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.tomar(99L)).isInstanceOf(NoEncontradoException.class);
  }

  // --- Liberar ---

  @Test
  void liberar_deSuCuadrilla_vuelveAPendienteYPublicaOrdenLiberada() {
    OrdenTrabajo ot = pendiente();
    ot.asignar(NORTE, HOY_MONTEVIDEO, AHORA.minusSeconds(60));
    ot.extraerEventos(ID_TECNICO);
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    servicio.liberar(1L);

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getIdCuadrilla()).isNull();
    verify(ordenTrabajoRepository).save(ot);
    verify(eventos)
        .publishEvent(
            new EstadoOrdenCambiado(1L, EstadoOT.ASIGNADA, EstadoOT.PENDIENTE, ID_TECNICO, AHORA));
    verify(eventos).publishEvent(new OrdenLiberada(1L, NORTE, ID_TECNICO, AHORA));
  }

  @Test
  void liberar_deOtraCuadrilla_403SinGuardar() {
    when(accesoOt.buscarParaModificar(1L))
        .thenThrow(new AccessDeniedException("La OT no está asignada a tu cuadrilla."));

    assertThatThrownBy(() -> servicio.liberar(1L)).isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(ordenTrabajoRepository, eventos);
  }

  @Test
  void liberar_pendiente_lanzaTransicionInvalidaSinGuardar() {
    OrdenTrabajo ot = pendiente();
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    assertThatThrownBy(() -> servicio.liberar(1L)).isInstanceOf(TransicionInvalidaException.class);

    verify(ordenTrabajoRepository, never()).save(any());
    verifyNoInteractions(eventos);
  }

  /** Preventiva pendiente ya «guardada» (con id y sin eventos pendientes). */
  private static OrdenTrabajo pendiente() {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, null, AHORA.minusSeconds(3600));
    ReflectionTestUtils.setField(ot, "id", 1L);
    ReflectionTestUtils.setField(ot, "numero", "OT-0001");
    ReflectionTestUtils.setField(ot, "creadoEn", AHORA.minusSeconds(3600));
    ot.extraerEventos(ID_TECNICO);
    return ot;
  }
}
