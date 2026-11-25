package com.ciemsa.sigma.ot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.ot.domain.Adjunto;
import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.EstadoOrdenCambiado;
import com.ciemsa.sigma.ot.domain.FotosFaltantesException;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import com.ciemsa.sigma.ot.domain.OrdenObservada;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.TipoImagen;
import com.ciemsa.sigma.ot.domain.TransicionInvalidaException;
import com.ciemsa.sigma.ot.infrastructure.AdjuntoRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ValidacionOtServiceTest {

  private static final Instant AHORA = Instant.parse("2026-11-25T15:00:00Z");
  private static final Instant CERRADA = AHORA.minusSeconds(3600);
  private static final Long ID_JEFATURA = 2L;
  private static final Long ID_TECNICO = 5L;
  private static final CierreTecnico CIERRE =
      new CierreTecnico("Lámpara roja quemada", "Cambio de lámpara", null);

  @Mock private OrdenTrabajoRepository ordenTrabajoRepository;
  @Mock private AdjuntoRepository adjuntoRepository;
  @Mock private AccesoOt accesoOt;
  @Mock private UsuarioActual usuarioActual;
  @Mock private ApplicationEventPublisher eventos;
  @Mock private ArmadorDetalleOt armadorDetalleOt;
  private ValidacionOtService servicio;
  private final List<Adjunto> fotos = new ArrayList<>();

  @BeforeEach
  void setUp() {
    lenient().when(usuarioActual.id()).thenReturn(ID_JEFATURA);
    lenient()
        .when(adjuntoRepository.findByIdOrdenTrabajoOrderByCreadoEnAscIdAsc(1L))
        .thenReturn(fotos);
    servicio =
        new ValidacionOtService(
            ordenTrabajoRepository,
            adjuntoRepository,
            accesoOt,
            usuarioActual,
            Clock.fixed(AHORA, ZoneOffset.UTC),
            eventos,
            armadorDetalleOt);
  }

  // --- Validar ---

  @Test
  void validar_conAntesYDespues_quedaValidadaYPublicaElCambioDeEstado() {
    OrdenTrabajo ot = cerrada();
    foto(MomentoFoto.ANTES, CERRADA);
    foto(MomentoFoto.DESPUES, CERRADA);
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    servicio.validar(1L, false);

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.VALIDADA);
    assertThat(ot.getValidadaEn()).isEqualTo(AHORA);
    verify(ordenTrabajoRepository).save(ot);
    verify(eventos)
        .publishEvent(
            new EstadoOrdenCambiado(
                1L, EstadoOT.CERRADA_TECNICA, EstadoOT.VALIDADA, ID_JEFATURA, AHORA));
  }

  @Test
  void validar_sinFotoDelDespues_409ConLosMomentosQueFaltanYNoValida() {
    OrdenTrabajo ot = cerrada();
    foto(MomentoFoto.ANTES, CERRADA);
    foto(MomentoFoto.DURANTE, CERRADA);
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    assertThatThrownBy(() -> servicio.validar(1L, false))
        .isInstanceOf(FotosFaltantesException.class)
        .hasMessageContaining("del después")
        .extracting("propiedades")
        .isEqualTo(Map.of("momentosFaltantes", List.of(MomentoFoto.DESPUES)));

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.CERRADA_TECNICA);
    verify(ordenTrabajoRepository, never()).save(any());
    verifyNoInteractions(eventos);
  }

  @Test
  void validar_sinFotosYConfirmado_quedaValidada() {
    OrdenTrabajo ot = cerrada();
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    servicio.validar(1L, true);

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.VALIDADA);
  }

  @Test
  void validar_noCerrada_lanzaTransicionInvalidaSinMirarLasFotos() {
    OrdenTrabajo ot = enProceso();
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    assertThatThrownBy(() -> servicio.validar(1L, false))
        .isInstanceOf(TransicionInvalidaException.class);

    verifyNoInteractions(adjuntoRepository, eventos);
  }

  // --- Observar ---

  @Test
  void observar_conMotivo_vuelveAAsignadaYPublicaLaObservacion() {
    OrdenTrabajo ot = cerrada();
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    servicio.observar(1L, "  Falta la foto del después  ");

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
    assertThat(ot.getIdCuadrilla()).isEqualTo(7L);
    verify(ordenTrabajoRepository).save(ot);
    verify(eventos)
        .publishEvent(
            new EstadoOrdenCambiado(
                1L, EstadoOT.CERRADA_TECNICA, EstadoOT.ASIGNADA, ID_JEFATURA, AHORA));
    verify(eventos)
        .publishEvent(new OrdenObservada(1L, "Falta la foto del después", ID_JEFATURA, AHORA));
  }

  @Test
  void observar_sinMotivo_lanzaReglaNegocioSinGuardar() {
    OrdenTrabajo ot = cerrada();
    when(accesoOt.buscarParaModificar(1L)).thenReturn(ot);

    assertThatThrownBy(() -> servicio.observar(1L, " ")).isInstanceOf(ReglaNegocioException.class);

    verify(ordenTrabajoRepository, never()).save(any());
    verifyNoInteractions(eventos);
  }

  // --- Fotos del último cierre (3a) ---

  @Test
  void momentosFaltantes_sinFotos_antesYDespuesEnEseOrden() {
    assertThat(servicio.momentosFaltantes(cerrada()))
        .containsExactly(MomentoFoto.ANTES, MomentoFoto.DESPUES);
  }

  @Test
  void momentosFaltantes_trasUnaObservacion_noCuentanLasFotosDelCierreRechazado() {
    OrdenTrabajo ot = cerrada();
    foto(MomentoFoto.ANTES, CERRADA);
    foto(MomentoFoto.DESPUES, CERRADA);
    Instant observada = CERRADA.plusSeconds(600);
    ot.observar("Las fotos no se ven", observada);
    ot.iniciar(observada.plusSeconds(60));
    ot.cerrarTecnicamente(CIERRE, UUID.randomUUID(), observada.plusSeconds(1200));
    foto(MomentoFoto.DESPUES, observada.plusSeconds(1200));

    assertThat(servicio.momentosFaltantes(ot)).containsExactly(MomentoFoto.ANTES);
  }

  /** Correctiva sin SLA, asignada a la cuadrilla 7 y cerrada técnicamente, ya «guardada». */
  private static OrdenTrabajo cerrada() {
    OrdenTrabajo ot = enProceso();
    ot.cerrarTecnicamente(CIERRE, UUID.randomUUID(), CERRADA);
    ot.extraerEventos(ID_TECNICO);
    return ot;
  }

  private static OrdenTrabajo enProceso() {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, null, CERRADA.minusSeconds(7200));
    ReflectionTestUtils.setField(ot, "id", 1L);
    ReflectionTestUtils.setField(ot, "numero", "OT-0001");
    ot.asignar(7L, LocalDate.parse("2026-11-25"), CERRADA.minusSeconds(7200));
    ot.iniciar(CERRADA.minusSeconds(3600));
    ot.extraerEventos(ID_TECNICO);
    return ot;
  }

  /** Una foto del cierre de la OT 1, guardada en {@code creadaEn}. */
  private void foto(MomentoFoto momento, Instant creadaEn) {
    Adjunto foto =
        Adjunto.fotoDeCierre(
            1L, momento, momento + ".jpg", TipoImagen.JPEG, 1024, "x/" + momento, ID_TECNICO);
    ReflectionTestUtils.setField(foto, "creadoEn", creadaEn);
    fotos.add(foto);
  }
}
