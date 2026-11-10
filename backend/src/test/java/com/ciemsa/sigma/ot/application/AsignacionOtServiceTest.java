package com.ciemsa.sigma.ot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.administracion.CuadrillaService;
import com.ciemsa.sigma.administracion.dto.CuadrillaResumen;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.OrdenAsignada;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AsignacionOtServiceTest {

  /** 9/11 a las 23:30 en Montevideo; en UTC ya es el 10/11. */
  private static final Instant AHORA = Instant.parse("2026-11-10T02:30:00Z");

  private static final LocalDate HOY_MONTEVIDEO = LocalDate.parse("2026-11-09");
  private static final CuadrillaResumen NORTE = new CuadrillaResumen(7L, "Cuadrilla Norte");

  @Mock private OrdenTrabajoRepository ordenTrabajoRepository;
  @Mock private CuadrillaService cuadrillaService;
  @Mock private ApplicationEventPublisher eventos;
  private AsignacionOtService servicio;

  @BeforeEach
  void setUp() {
    servicio =
        new AsignacionOtService(
            ordenTrabajoRepository,
            cuadrillaService,
            Clock.fixed(AHORA, ZoneOffset.UTC),
            ZoneId.of("America/Montevideo"),
            eventos);
  }

  @Test
  void asignar_variasPendientes_quedanAsignadasYPublicaOrdenAsignada() {
    OrdenTrabajo ot1 = ot(1L, "OT-0001");
    OrdenTrabajo ot2 = ot(2L, "OT-0002");
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findAllById(any())).thenReturn(List.of(ot2, ot1));

    List<DetalleOt> resultado = servicio.asignar(List.of(1L, 2L), 7L, HOY_MONTEVIDEO);

    assertThat(resultado).extracting(d -> d.ordenTrabajo().getId()).containsExactly(1L, 2L);
    assertThat(List.of(ot1, ot2))
        .allSatisfy(
            ot -> {
              assertThat(ot.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
              assertThat(ot.getIdCuadrilla()).isEqualTo(7L);
            });
    verify(eventos).publishEvent(new OrdenAsignada(1L, null, 7L, HOY_MONTEVIDEO, AHORA));
    verify(eventos).publishEvent(new OrdenAsignada(2L, null, 7L, HOY_MONTEVIDEO, AHORA));
  }

  @Test
  void asignar_hoyEnMontevideoAunqueEnUtcSeaManana_seAcepta() {
    OrdenTrabajo ot1 = ot(1L, "OT-0001");
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findAllById(any())).thenReturn(List.of(ot1));

    servicio.asignar(List.of(1L), 7L, HOY_MONTEVIDEO);

    assertThat(ot1.getFechaEjecucion()).isEqualTo(HOY_MONTEVIDEO);
  }

  @Test
  void asignar_fechaAnteriorAHoy_lanzaFechaAnteriorSinBuscarOts() {
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));

    assertThatThrownBy(() -> servicio.asignar(List.of(1L), 7L, HOY_MONTEVIDEO.minusDays(1)))
        .isInstanceOf(ReglaNegocioException.class)
        .extracting("tipo")
        .isEqualTo("fecha-anterior");
    verifyNoInteractions(ordenTrabajoRepository);
  }

  @Test
  void asignar_cuadrillaInactivaOInexistente_lanzaReglaNegocio() {
    when(cuadrillaService.buscarActiva(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.asignar(List.of(1L), 9L, HOY_MONTEVIDEO))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("cuadrilla 9");
    verifyNoInteractions(ordenTrabajoRepository);
  }

  @Test
  void asignar_otsInexistentes_lanzaNoEncontradoConTodasLasFaltantes() {
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findAllById(any())).thenReturn(List.of(ot(1L, "OT-0001")));

    assertThatThrownBy(() -> servicio.asignar(List.of(1L, 99L, 100L), 7L, HOY_MONTEVIDEO))
        .isInstanceOf(NoEncontradoException.class)
        .hasMessageContaining("[99, 100]");
  }

  @Test
  void asignar_unaEnProceso_lanzaConSuNumeroYNoGuardaNiPublicaNada() {
    OrdenTrabajo pendiente = ot(1L, "OT-0001");
    OrdenTrabajo enProceso = ot(2L, "OT-0002");
    enProceso.asignar(8L, HOY_MONTEVIDEO, AHORA);
    enProceso.iniciar(AHORA);
    enProceso.extraerEventos();
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findAllById(any())).thenReturn(List.of(pendiente, enProceso));

    assertThatThrownBy(() -> servicio.asignar(List.of(1L, 2L), 7L, HOY_MONTEVIDEO))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageStartingWith("OT-0002:")
        .extracting("tipo")
        .isEqualTo("transicion-invalida");
    verify(ordenTrabajoRepository, never()).saveAll(any());
    verifyNoInteractions(eventos);
  }

  @Test
  void asignar_idsRepetidos_asignaCadaOtUnaVez() {
    OrdenTrabajo ot1 = ot(1L, "OT-0001");
    when(cuadrillaService.buscarActiva(7L)).thenReturn(Optional.of(NORTE));
    when(ordenTrabajoRepository.findAllById(any())).thenReturn(List.of(ot1));

    List<DetalleOt> resultado = servicio.asignar(List.of(1L, 1L), 7L, HOY_MONTEVIDEO);

    assertThat(resultado).hasSize(1);
    verify(eventos).publishEvent(new OrdenAsignada(1L, null, 7L, HOY_MONTEVIDEO, AHORA));
  }

  /** OT preventiva pendiente, como si ya estuviera guardada (con id, número y fecha de alta). */
  private static OrdenTrabajo ot(Long id, String numero) {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, null, AHORA.minusSeconds(3600));
    ReflectionTestUtils.setField(ot, "id", id);
    ReflectionTestUtils.setField(ot, "numero", numero);
    ReflectionTestUtils.setField(ot, "creadoEn", AHORA.minusSeconds(3600));
    ot.extraerEventos();
    return ot;
  }
}
