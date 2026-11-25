package com.ciemsa.sigma.activos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.activos.dto.InstalacionFicha;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstalacionServiceTest {

  @Mock private InstalacionRepository instalacionRepository;
  @InjectMocks private InstalacionService instalacionService;

  @Test
  void patronSinTildes_conMayusculasYTildes_lasQuita() {
    assertThat(InstalacionService.patronSinTildes("  Peñarol ÁVILA ")).isEqualTo("penarol avila");
  }

  @Test
  void patronSinTildes_conComodines_losEscapa() {
    assertThat(InstalacionService.patronSinTildes("10%_a!b")).isEqualTo("10!%!_a!!b");
  }

  @Test
  void obtenerFicha_existente_devuelveSusDatosSinTipoNiDireccion() {
    Instalacion instalacion =
        Instalacion.crear(
            "MVD-1",
            "Cruce de prueba",
            "Montevideo",
            new BigDecimal("-34.900000"),
            new BigDecimal("-56.100000"),
            3L);
    when(instalacionRepository.findById(7L)).thenReturn(Optional.of(instalacion));

    InstalacionFicha ficha = instalacionService.obtenerFicha(7L);

    assertThat(ficha.codigo()).isEqualTo("MVD-1");
    assertThat(ficha.idOrganismo()).isEqualTo(3L);
    assertThat(ficha.tipo()).isNull();
    assertThat(ficha.direccion()).isNull();
    assertThat(ficha.activa()).isTrue();
  }

  @Test
  void obtenerFicha_inexistente_lanzaNoEncontrado() {
    when(instalacionRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> instalacionService.obtenerFicha(99L))
        .isInstanceOf(NoEncontradoException.class)
        .hasMessageContaining("99");
  }
}
