package com.ciemsa.sigma.activos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstalacionServiceTest {

  @Test
  void patronSinTildes_conMayusculasYTildes_lasQuita() {
    assertThat(InstalacionService.patronSinTildes("  Peñarol ÁVILA ")).isEqualTo("penarol avila");
  }

  @Test
  void patronSinTildes_conComodines_losEscapa() {
    assertThat(InstalacionService.patronSinTildes("10%_a!b")).isEqualTo("10!%!_a!!b");
  }
}
