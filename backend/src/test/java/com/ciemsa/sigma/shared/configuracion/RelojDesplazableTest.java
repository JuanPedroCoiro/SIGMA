package com.ciemsa.sigma.shared.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class RelojDesplazableTest {

  private static final Instant AHORA = Instant.parse("2026-11-15T15:00:00Z");
  private static final Instant AYER = AHORA.minus(Duration.ofDays(1));

  private final RelojDesplazable reloj = new RelojDesplazable(Clock.fixed(AHORA, ZoneOffset.UTC));

  @Test
  void fueraDeEnDaLaHoraDeSuBase() {
    assertThat(reloj.instant()).isEqualTo(AHORA);
    assertThat(reloj.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void dentroDeEnDaLaHoraPedidaYDespuesVuelve() {
    assertThat(reloj.en(AYER, reloj::instant)).isEqualTo(AYER);
    assertThat(reloj.instant()).isEqualTo(AHORA);
  }

  @Test
  void vuelveAunqueLaAccionFalle() {
    try {
      reloj.en(
          AYER,
          () -> {
            throw new IllegalStateException("falla");
          });
    } catch (IllegalStateException esperada) {
      // la hora tiene que volver igual
    }
    assertThat(reloj.instant()).isEqualTo(AHORA);
  }

  @Test
  void soloCorreElRelojDelHiloActual() {
    Instant enOtroHilo = reloj.en(AYER, () -> CompletableFuture.supplyAsync(reloj::instant).join());
    assertThat(enOtroHilo).isEqualTo(AHORA);
  }

  @Test
  void anidadoRestauraElDesplazamientoAnterior() {
    Instant haceUnaHora = AHORA.minus(Duration.ofHours(1));
    Instant despuesDelAnidado =
        reloj.en(
            AYER,
            () -> {
              reloj.en(haceUnaHora, reloj::instant);
              return reloj.instant();
            });
    assertThat(despuesDelAnidado).isEqualTo(AYER);
  }
}
