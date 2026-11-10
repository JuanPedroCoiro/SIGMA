package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrdenTrabajoTest {

  private static final Instant INICIO = Instant.parse("2026-11-02T13:00:00Z");
  private static final Instant LIMITE = Instant.parse("2026-11-02T21:00:00Z");
  private static final LocalDate FECHA = LocalDate.parse("2026-11-03");

  // --- Vencimiento (condición derivada) ---

  @Test
  void correctiva_conRegla_fechaLimiteSegunLaRegla() {
    OrdenTrabajo ot = correctiva(ReglaSLA.crear(1L, Prioridad.ALTA, 2, 8));

    assertThat(ot.getTipo()).isEqualTo(TipoOT.CORRECTIVA);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getInicioSla()).isEqualTo(INICIO);
    assertThat(ot.getFechaLimite()).isEqualTo(LIMITE);
    assertThat(ot.getIdInstalacion()).isEqualTo(10L);
  }

  @Test
  void correctiva_sinRegla_quedaConSlaSinDefinir() {
    OrdenTrabajo ot = correctiva(null);

    assertThat(ot.getFechaLimite()).isNull();
    assertThat(ot.estaVencida(LIMITE.plusSeconds(3600 * 24 * 30))).isFalse();
  }

  @Test
  void preventiva_noTieneSlaNiIncidente() {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, FECHA, INICIO);

    assertThat(ot.getTipo()).isEqualTo(TipoOT.PREVENTIVA);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getIncidente()).isNull();
    assertThat(ot.getInicioSla()).isNull();
    assertThat(ot.getFechaLimite()).isNull();
    assertThat(ot.estaVencida(INICIO.plusSeconds(3600 * 24 * 365))).isFalse();
  }

  @Test
  void estaVencida_antesYDespuesDelLimite() {
    OrdenTrabajo ot = correctiva(ReglaSLA.crear(1L, Prioridad.ALTA, 2, 8));

    assertThat(ot.estaVencida(LIMITE.minusSeconds(1))).isFalse();
    assertThat(ot.estaVencida(LIMITE)).isFalse();
    assertThat(ot.estaVencida(LIMITE.plusSeconds(1))).isTrue();
  }

  @Test
  void estaVencida_otCerrada_noVence() {
    OrdenTrabajo ot = correctiva(ReglaSLA.crear(1L, Prioridad.ALTA, 2, 8));
    ot.asignar(7L, FECHA, INICIO);
    ot.iniciar(INICIO);
    ot.cerrarTecnicamente(INICIO);

    assertThat(ot.estaVencida(LIMITE.plusSeconds(1))).isFalse();
  }

  @Test
  void estaVencida_otEnPausaPorClima_sigueVenciendo() {
    OrdenTrabajo ot = correctiva(ReglaSLA.crear(1L, Prioridad.ALTA, 2, 8));
    ot.pausar(MotivoPausa.CLIMA, null, 3L, INICIO);

    assertThat(ot.estaVencida(LIMITE.plusSeconds(1))).isTrue();
  }

  // --- Eventos de dominio ---

  @Test
  void extraerEventos_generadaYCambios_enOrdenYLuegoVacia() {
    OrdenTrabajo ot = correctiva(null);
    Instant asignada = INICIO.plusSeconds(60);
    Instant iniciada = INICIO.plusSeconds(120);
    ot.asignar(7L, FECHA, asignada);
    ot.iniciar(iniciada);
    ReflectionTestUtils.setField(ot, "id", 99L);

    assertThat(ot.extraerEventos())
        .containsExactly(
            new OrdenGenerada(99L, null, INICIO),
            new EstadoOrdenCambiado(99L, EstadoOT.PENDIENTE, EstadoOT.ASIGNADA, asignada),
            new EstadoOrdenCambiado(99L, EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, iniciada));
    assertThat(ot.extraerEventos()).isEmpty();
  }

  @Test
  void extraerEventos_transicionInvalida_noAgregaEvento() {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, FECHA, INICIO);
    ReflectionTestUtils.setField(ot, "id", 5L);
    ot.extraerEventos();

    assertThatThrownBy(() -> ot.validar(INICIO)).isInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.extraerEventos()).isEmpty();
  }

  @Test
  void extraerEventos_otSinGuardar_lanzaIllegalState() {
    OrdenTrabajo ot = OrdenTrabajo.preventiva(10L, FECHA, INICIO);

    assertThatThrownBy(ot::extraerEventos).isInstanceOf(IllegalStateException.class);
  }

  private static OrdenTrabajo correctiva(ReglaSLA regla) {
    return Incidente.registrar(10L, OrigenIncidente.MONITOREO, 2L, Prioridad.ALTA, "Apagado", 3L)
        .generarOrdenCorrectiva(INICIO, regla);
  }

  // --- Tramos por cuadrilla (HU-05) ---

  @Test
  void asignar_pendiente_abreUnTramoDeLaCuadrilla() {
    OrdenTrabajo ot = correctiva(null);

    ot.asignar(7L, FECHA, INICIO);

    assertThat(ot.getTramos())
        .singleElement()
        .satisfies(
            tramo -> {
              assertThat(tramo.getIdCuadrilla()).isEqualTo(7L);
              assertThat(tramo.getDesde()).isEqualTo(INICIO);
              assertThat(tramo.estaAbierto()).isTrue();
            });
  }

  @Test
  void asignar_otraCuadrilla_cierraElTramoAnteriorYAbreOtro() {
    OrdenTrabajo ot = correctiva(null);
    Instant despues = INICIO.plusSeconds(3600);
    ot.asignar(7L, FECHA, INICIO);

    ot.asignar(8L, FECHA, despues);

    assertThat(ot.getTramos()).hasSize(2);
    assertThat(ot.getTramos().getFirst().getHasta()).isEqualTo(despues);
    assertThat(ot.tramoAbierto())
        .hasValueSatisfying(tramo -> assertThat(tramo.getIdCuadrilla()).isEqualTo(8L));
  }

  @Test
  void asignar_mismaCuadrillaOtraFecha_soloCambiaLaFecha() {
    OrdenTrabajo ot = correctiva(null);
    ot.asignar(7L, FECHA, INICIO);

    ot.asignar(7L, FECHA.plusDays(1), INICIO.plusSeconds(3600));

    assertThat(ot.getTramos()).hasSize(1);
    assertThat(ot.getFechaEjecucion()).isEqualTo(FECHA.plusDays(1));
  }

  @Test
  void liberar_asignada_cierraElTramo() {
    OrdenTrabajo ot = correctiva(null);
    ot.asignar(7L, FECHA, INICIO);

    ot.liberar(INICIO.plusSeconds(3600));

    assertThat(ot.tramoAbierto()).isEmpty();
    assertThat(ot.getTramos().getFirst().getHasta()).isEqualTo(INICIO.plusSeconds(3600));
  }

  @Test
  void anular_asignada_cierraElTramo() {
    OrdenTrabajo ot = correctiva(null);
    ot.asignar(7L, FECHA, INICIO);

    ot.anular("Incidente duplicado", 3L, INICIO.plusSeconds(3600));

    assertThat(ot.tramoAbierto()).isEmpty();
  }

  @Test
  void anular_sinMotivo_noCierraElTramo() {
    OrdenTrabajo ot = correctiva(null);
    ot.asignar(7L, FECHA, INICIO);

    assertThatThrownBy(() -> ot.anular(" ", 3L, INICIO.plusSeconds(3600)))
        .isInstanceOf(ReglaNegocioException.class);
    assertThat(ot.tramoAbierto()).isPresent();
  }
}
