package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Pausas de la OT con motivo (precisiones del dominio del 01/11): SLA, vencimiento y tiempos. */
class PausaOTTest {

  private static final Long USUARIO = 3L;
  private static final Long OTRO_USUARIO = 4L;

  // Hora de Montevideo (UTC-3), como en el ejemplo de la regla
  private static final Instant LUN_08 = montevideo("2026-11-02T08:00");
  private static final Instant LUN_12 = montevideo("2026-11-02T12:00");
  private static final Instant MAR_10 = montevideo("2026-11-03T10:00");

  // --- Motivos ---

  @Test
  void motivos_soloTerceroReiniciaElSlaYTerceroYOtroPidenDetalle() {
    assertThat(MotivoPausa.values())
        .filteredOn(MotivoPausa::reiniciaSla)
        .containsExactly(MotivoPausa.TERCERO);
    assertThat(MotivoPausa.values())
        .filteredOn(MotivoPausa::requiereDetalle)
        .containsExactly(MotivoPausa.TERCERO, MotivoPausa.OTRO);
  }

  // --- SLA ---

  @Test
  void reanudar_trasTercero_elSlaArrancaCompleto() {
    // SLA 24 h: OT lun 8:00; lun 12:00 pausa por UTE; reanuda mar 10:00 → vence mié 10:00
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    assertThat(ot.getFechaLimite()).isEqualTo(montevideo("2026-11-03T08:00"));

    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);
    ot.reanudar(OTRO_USUARIO, MAR_10);

    assertThat(ot.getInicioSla()).isEqualTo(MAR_10);
    assertThat(ot.getFechaLimite()).isEqualTo(montevideo("2026-11-04T10:00"));
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getPausas())
        .singleElement()
        .satisfies(p -> assertThat(p.getDetalle()).isEqualTo("UTE"))
        .satisfies(p -> assertThat(p.getIdUsuarioPausa()).isEqualTo(USUARIO))
        .satisfies(p -> assertThat(p.getIdUsuarioReanudacion()).isEqualTo(OTRO_USUARIO))
        .satisfies(p -> assertThat(p.getFin()).isEqualTo(MAR_10));
  }

  @ParameterizedTest
  @EnumSource(
      value = MotivoPausa.class,
      names = {"MATERIALES", "CLIMA", "FIN_DE_JORNADA"})
  void reanudar_otroMotivo_laFechaLimiteNoCambia(MotivoPausa motivo) {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    Instant limite = ot.getFechaLimite();

    ot.pausar(motivo, null, USUARIO, LUN_12);
    ot.reanudar(USUARIO, MAR_10);

    assertThat(ot.getInicioSla()).isEqualTo(LUN_08);
    assertThat(ot.getFechaLimite()).isEqualTo(limite);
  }

  @Test
  void reanudar_trasTerceroSinSla_sigueSinDefinir() {
    OrdenTrabajo ot = correctivaSinSla(LUN_08);

    ot.pausar(MotivoPausa.TERCERO, "Antel", USUARIO, LUN_12);
    ot.reanudar(USUARIO, MAR_10);

    assertThat(ot.getFechaLimite()).isNull();
    assertThat(ot.estaVencida(MAR_10.plus(Duration.ofDays(30)))).isFalse();
  }

  @Test
  void reanudar_vuelveAlEstadoAnterior() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ot.asignar(7L, LocalDate.parse("2026-11-03"), LUN_08);

    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.EN_PAUSA);
    ot.reanudar(USUARIO, MAR_10);

    assertThat(ot.getEstado()).isEqualTo(EstadoOT.ASIGNADA);
    assertThat(ot.getIdCuadrilla()).isEqualTo(7L);
  }

  // --- Vencimiento ---

  @Test
  void estaVencida_enPausaPorTercero_noVence() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);

    assertThat(ot.estaVencida(ot.getFechaLimite().plus(Duration.ofDays(3)))).isFalse();
  }

  @Test
  void estaVencida_enPausaPorClima_sigueVenciendo() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ot.pausar(MotivoPausa.CLIMA, null, USUARIO, LUN_12);

    assertThat(ot.estaVencida(ot.getFechaLimite().plusSeconds(1))).isTrue();
  }

  @Test
  void estaVencida_reanudadaTrasTercero_venceConElPlazoNuevo() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);
    ot.reanudar(USUARIO, MAR_10);

    assertThat(ot.estaVencida(montevideo("2026-11-04T09:59"))).isFalse();
    assertThat(ot.estaVencida(montevideo("2026-11-04T10:01"))).isTrue();
  }

  // --- Detalle y transiciones ---

  @ParameterizedTest
  @EnumSource(
      value = MotivoPausa.class,
      names = {"TERCERO", "OTRO"})
  void pausar_sinDetalleCuandoLoRequiere_lanzaReglaNegocioSinPausar(MotivoPausa motivo) {
    OrdenTrabajo ot = correctiva(LUN_08, 24);

    assertThatThrownBy(() -> ot.pausar(motivo, "  ", USUARIO, LUN_12))
        .isInstanceOf(ReglaNegocioException.class)
        .isNotInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getPausas()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = MotivoPausa.class,
      names = {"MATERIALES", "CLIMA", "FIN_DE_JORNADA"})
  void pausar_sinDetalleCuandoEsOpcional_pausa(MotivoPausa motivo) {
    OrdenTrabajo ot = correctiva(LUN_08, 24);

    assertThatCode(() -> ot.pausar(motivo, null, USUARIO, LUN_12)).doesNotThrowAnyException();
    assertThat(ot.pausaAbierta()).map(PausaOT::getMotivo).contains(motivo);
  }

  @Test
  void pausar_recortaElDetalle() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);

    ot.pausar(MotivoPausa.OTRO, "  Corte de calle  ", USUARIO, LUN_12);

    assertThat(ot.pausaAbierta()).map(PausaOT::getDetalle).contains("Corte de calle");
  }

  @Test
  void pausar_enPausa_lanzaTransicionInvalida() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ot.pausar(MotivoPausa.CLIMA, null, USUARIO, LUN_12);

    assertThatThrownBy(() -> ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, MAR_10))
        .isInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.getPausas()).hasSize(1);
  }

  @Test
  void reanudar_sinPausa_lanzaTransicionInvalida() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);

    assertThatThrownBy(() -> ot.reanudar(USUARIO, LUN_12))
        .isInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
  }

  // --- Tiempos ---

  @Test
  void tiempos_dosPausasDeMotivosDistintos_totalEnPausaYEfectivo() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ReflectionTestUtils.setField(ot, "creadoEn", LUN_08);
    ot.pausar(MotivoPausa.CLIMA, null, USUARIO, LUN_08.plus(Duration.ofHours(1)));
    ot.reanudar(USUARIO, LUN_08.plus(Duration.ofHours(3))); // 2 h por clima
    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);

    TiemposOT tiempos = ot.tiempos(LUN_12.plus(Duration.ofMinutes(90))); // abierta hace 1:30

    assertThat(tiempos.total()).isEqualTo(Duration.ofMinutes(5 * 60 + 30));
    assertThat(tiempos.enPausa())
        .isEqualTo(
            Map.of(
                MotivoPausa.CLIMA, Duration.ofHours(2),
                MotivoPausa.TERCERO, Duration.ofMinutes(90)));
    assertThat(tiempos.efectivo()).isEqualTo(Duration.ofMinutes(2 * 60));
  }

  @Test
  void tiempos_sinPausas_efectivoIgualAlTotal() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ReflectionTestUtils.setField(ot, "creadoEn", LUN_08);

    TiemposOT tiempos = ot.tiempos(LUN_12);

    assertThat(tiempos.total()).isEqualTo(Duration.ofHours(4));
    assertThat(tiempos.enPausa()).isEmpty();
    assertThat(tiempos.efectivo()).isEqualTo(Duration.ofHours(4));
  }

  @Test
  void tiempos_otAnulada_null() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ReflectionTestUtils.setField(ot, "creadoEn", LUN_08);
    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);
    ot.anular("El tercero resolvió la falla", USUARIO, MAR_10);

    assertThat(ot.tiempos(MAR_10.plus(Duration.ofDays(10)))).isNull();
  }

  // --- Eventos ---

  @Test
  void extraerEventos_pausarYReanudar_cambiosDeEstadoYEventosDePausa() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ReflectionTestUtils.setField(ot, "id", 99L);
    ot.extraerEventos();

    ot.pausar(MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12);
    ot.reanudar(OTRO_USUARIO, MAR_10);

    assertThat(ot.extraerEventos())
        .containsExactly(
            new EstadoOrdenCambiado(99L, EstadoOT.PENDIENTE, EstadoOT.EN_PAUSA, LUN_12),
            new OrdenPausada(99L, MotivoPausa.TERCERO, "UTE", USUARIO, LUN_12),
            new EstadoOrdenCambiado(99L, EstadoOT.EN_PAUSA, EstadoOT.PENDIENTE, MAR_10),
            new OrdenReanudada(
                99L, MotivoPausa.TERCERO, OTRO_USUARIO, MAR_10, montevideo("2026-11-04T10:00")));
  }

  @Test
  void extraerEventos_pausaRechazada_noAgregaEventos() {
    OrdenTrabajo ot = correctiva(LUN_08, 24);
    ReflectionTestUtils.setField(ot, "id", 99L);
    ot.extraerEventos();

    assertThatThrownBy(() -> ot.pausar(MotivoPausa.TERCERO, null, USUARIO, LUN_12))
        .isInstanceOf(ReglaNegocioException.class);
    assertThat(ot.extraerEventos()).isEmpty();
  }

  private static OrdenTrabajo correctiva(Instant inicio, int horasResolucion) {
    return incidente()
        .generarOrdenCorrectiva(inicio, ReglaSLA.crear(1L, Prioridad.ALTA, 1, horasResolucion));
  }

  private static OrdenTrabajo correctivaSinSla(Instant inicio) {
    return incidente().generarOrdenCorrectiva(inicio, null);
  }

  private static Incidente incidente() {
    return Incidente.registrar(10L, OrigenIncidente.MONITOREO, 2L, Prioridad.ALTA, "Apagado", 3L);
  }

  private static Instant montevideo(String fechaHora) {
    return OffsetDateTime.parse(fechaHora + "-03:00").toInstant();
  }
}
