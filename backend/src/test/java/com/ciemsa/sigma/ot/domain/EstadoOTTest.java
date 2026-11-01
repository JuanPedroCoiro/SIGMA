package com.ciemsa.sigma.ot.domain;

import static com.ciemsa.sigma.ot.domain.EstadoOT.ANULADA;
import static com.ciemsa.sigma.ot.domain.EstadoOT.ASIGNADA;
import static com.ciemsa.sigma.ot.domain.EstadoOT.CERRADA_TECNICA;
import static com.ciemsa.sigma.ot.domain.EstadoOT.EN_PAUSA;
import static com.ciemsa.sigma.ot.domain.EstadoOT.EN_PROCESO;
import static com.ciemsa.sigma.ot.domain.EstadoOT.PENDIENTE;
import static com.ciemsa.sigma.ot.domain.EstadoOT.VALIDADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Todas las combinaciones estado × operación de la Fig. 18. */
class EstadoOTTest {

  private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");
  private static final LocalDate FECHA = LocalDate.parse("2026-11-03");
  private static final Long CUADRILLA = 7L;
  private static final Long OTRA_CUADRILLA = 8L;

  enum Operacion {
    ASIGNAR(ot -> ot.asignar(OTRA_CUADRILLA, FECHA, AHORA)),
    LIBERAR(ot -> ot.liberar(AHORA)),
    INICIAR(ot -> ot.iniciar(AHORA)),
    PAUSAR(ot -> ot.pausar(AHORA)),
    REANUDAR(ot -> ot.reanudar(AHORA)),
    CERRAR(ot -> ot.cerrarTecnicamente(AHORA)),
    VALIDAR(ot -> ot.validar(AHORA)),
    OBSERVAR(ot -> ot.observar(AHORA)),
    ANULAR(ot -> ot.anular("Duplicada", AHORA));

    private final Consumer<OrdenTrabajo> accion;

    Operacion(Consumer<OrdenTrabajo> accion) {
      this.accion = accion;
    }
  }

  /** Transiciones válidas; EN_PAUSA se arma desde EN_PROCESO (anular no se permite). */
  private static final Map<EstadoOT, Map<Operacion, EstadoOT>> VALIDAS =
      Map.of(
          PENDIENTE,
          Map.of(
              Operacion.ASIGNAR, ASIGNADA, Operacion.PAUSAR, EN_PAUSA, Operacion.ANULAR, ANULADA),
          ASIGNADA,
          Map.of(
              Operacion.ASIGNAR, ASIGNADA,
              Operacion.LIBERAR, PENDIENTE,
              Operacion.INICIAR, EN_PROCESO,
              Operacion.PAUSAR, EN_PAUSA,
              Operacion.ANULAR, ANULADA),
          EN_PROCESO,
          Map.of(
              Operacion.LIBERAR, PENDIENTE,
              Operacion.PAUSAR, EN_PAUSA,
              Operacion.CERRAR, CERRADA_TECNICA),
          EN_PAUSA,
          Map.of(Operacion.REANUDAR, EN_PROCESO),
          CERRADA_TECNICA,
          Map.of(Operacion.VALIDAR, VALIDADA, Operacion.OBSERVAR, ASIGNADA),
          VALIDADA,
          Map.of(),
          ANULADA,
          Map.of());

  static Stream<Arguments> combinaciones() {
    return Arrays.stream(EstadoOT.values())
        .flatMap(e -> Arrays.stream(Operacion.values()).map(op -> Arguments.of(e, op)));
  }

  @ParameterizedTest(name = "{0} + {1}")
  @MethodSource("combinaciones")
  void operacion_segunElEstado_transicionaOLanzaTransicionInvalida(
      EstadoOT inicial, Operacion operacion) {
    OrdenTrabajo ot = otEn(inicial);
    EstadoOT esperado = VALIDAS.get(inicial).get(operacion);

    if (esperado == null) {
      assertThatThrownBy(() -> operacion.accion.accept(ot))
          .isInstanceOf(TransicionInvalidaException.class)
          .extracting("tipo")
          .isEqualTo("transicion-invalida");
      assertThat(ot.getEstado()).isEqualTo(inicial);
    } else {
      operacion.accion.accept(ot);
      assertThat(ot.getEstado()).isEqualTo(esperado);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOT.class,
      names = {"PENDIENTE", "ASIGNADA", "EN_PROCESO"})
  void reanudar_vuelveAlEstadoAnteriorALaPausa(EstadoOT anterior) {
    OrdenTrabajo ot = otEn(anterior);
    ot.pausar(AHORA);

    assertThat(ot.getEstadoAnteriorPausa()).isEqualTo(anterior);

    ot.reanudar(AHORA);

    assertThat(ot.getEstado()).isEqualTo(anterior);
    assertThat(ot.getEstadoAnteriorPausa()).isNull();
  }

  @Test
  void asignar_pendiente_guardaCuadrillaYFecha() {
    OrdenTrabajo ot = otEn(PENDIENTE);

    ot.asignar(CUADRILLA, FECHA, AHORA);

    assertThat(ot.getIdCuadrilla()).isEqualTo(CUADRILLA);
    assertThat(ot.getFechaEjecucion()).isEqualTo(FECHA);
  }

  @Test
  void asignar_asignada_reasignaAOtraCuadrilla() {
    OrdenTrabajo ot = otEn(ASIGNADA);

    ot.asignar(OTRA_CUADRILLA, FECHA, AHORA);

    assertThat(ot.getEstado()).isEqualTo(ASIGNADA);
    assertThat(ot.getIdCuadrilla()).isEqualTo(OTRA_CUADRILLA);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOT.class,
      names = {"ASIGNADA", "EN_PROCESO"})
  void liberar_quitaLaCuadrilla(EstadoOT inicial) {
    OrdenTrabajo ot = otEn(inicial);

    ot.liberar(AHORA);

    assertThat(ot.getEstado()).isEqualTo(PENDIENTE);
    assertThat(ot.getIdCuadrilla()).isNull();
  }

  @Test
  void observar_cerradaTecnica_vuelveAsignadaALaMismaCuadrilla() {
    OrdenTrabajo ot = otEn(CERRADA_TECNICA);

    ot.observar(AHORA);

    assertThat(ot.getEstado()).isEqualTo(ASIGNADA);
    assertThat(ot.getIdCuadrilla()).isEqualTo(CUADRILLA);
  }

  @ParameterizedTest
  @EnumSource(
      value = EstadoOT.class,
      names = {"PENDIENTE", "ASIGNADA"})
  void anular_enPausaSinHaberEmpezado_anulaYCierraLaPausa(EstadoOT anterior) {
    OrdenTrabajo ot = otEn(anterior);
    ot.pausar(AHORA);

    ot.anular("El tercero resolvió la falla", AHORA);

    assertThat(ot.getEstado()).isEqualTo(ANULADA);
    assertThat(ot.getEstadoAnteriorPausa()).isNull();
    assertThat(ot.getMotivoAnulacion()).isEqualTo("El tercero resolvió la falla");
  }

  @Test
  void anular_enPausaDesdeEnProceso_lanzaTransicionInvalida() {
    OrdenTrabajo ot = otEn(EN_PROCESO);
    ot.pausar(AHORA);

    assertThatThrownBy(() -> ot.anular("Ya no hace falta", AHORA))
        .isInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.getEstado()).isEqualTo(EN_PAUSA);
    assertThat(ot.getEstadoAnteriorPausa()).isEqualTo(EN_PROCESO);
  }

  @Test
  void anular_enProceso_lanzaTransicionInvalida() {
    OrdenTrabajo ot = otEn(EN_PROCESO);

    assertThatThrownBy(() -> ot.anular("Ya no hace falta", AHORA))
        .isInstanceOf(TransicionInvalidaException.class);
  }

  @Test
  void anular_sinMotivo_lanzaReglaNegocio() {
    OrdenTrabajo ot = otEn(PENDIENTE);

    assertThatThrownBy(() -> ot.anular("  ", AHORA))
        .isInstanceOf(ReglaNegocioException.class)
        .isNotInstanceOf(TransicionInvalidaException.class);
    assertThat(ot.getEstado()).isEqualTo(PENDIENTE);
  }

  @Test
  void estaAbierta_soloLosEstadosSinResolver() {
    assertThat(Arrays.stream(EstadoOT.values()).filter(EstadoOT::estaAbierta))
        .containsExactly(PENDIENTE, ASIGNADA, EN_PROCESO, EN_PAUSA);
  }

  /** Lleva una OT nueva al estado pedido por transiciones válidas. */
  static OrdenTrabajo otEn(EstadoOT estado) {
    OrdenTrabajo ot =
        Incidente.registrar(1L, OrigenIncidente.MONITOREO, 2L, Prioridad.ALTA, "Apagado", 3L)
            .generarOrdenCorrectiva(AHORA, null);
    switch (estado) {
      case PENDIENTE -> {}
      case ASIGNADA -> ot.asignar(CUADRILLA, FECHA, AHORA);
      case EN_PROCESO -> {
        ot.asignar(CUADRILLA, FECHA, AHORA);
        ot.iniciar(AHORA);
      }
      case EN_PAUSA -> {
        ot.asignar(CUADRILLA, FECHA, AHORA);
        ot.iniciar(AHORA);
        ot.pausar(AHORA);
      }
      case CERRADA_TECNICA -> {
        ot.asignar(CUADRILLA, FECHA, AHORA);
        ot.iniciar(AHORA);
        ot.cerrarTecnicamente(AHORA);
      }
      case VALIDADA -> {
        ot.asignar(CUADRILLA, FECHA, AHORA);
        ot.iniciar(AHORA);
        ot.cerrarTecnicamente(AHORA);
        ot.validar(AHORA);
      }
      case ANULADA -> ot.anular("Duplicada", AHORA);
    }
    return ot;
  }
}
