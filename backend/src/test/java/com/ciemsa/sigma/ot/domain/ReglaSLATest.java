package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReglaSLATest {

  private static final Instant INICIO = Instant.parse("2026-11-02T13:00:00Z");

  @Test
  void calcularVencimiento_sumaLasHorasDeResolucion() {
    ReglaSLA regla = ReglaSLA.crear(1L, Prioridad.ALTA, 2, 8);

    assertThat(regla.calcularVencimiento(INICIO)).isEqualTo(Instant.parse("2026-11-02T21:00:00Z"));
  }

  @Test
  void calcularVencimiento_plazoDeVariosDias_cruzaElCambioDeDia() {
    ReglaSLA regla = ReglaSLA.crear(1L, Prioridad.BAJA, 24, 120);

    assertThat(regla.calcularVencimiento(INICIO)).isEqualTo(Instant.parse("2026-11-07T13:00:00Z"));
  }
}
