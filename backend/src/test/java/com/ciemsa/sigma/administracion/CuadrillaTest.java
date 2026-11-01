package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import org.junit.jupiter.api.Test;

class CuadrillaTest {

  @Test
  void crear_nombreConEspaciosYZonaEnBlanco_normalizaYQuedaActiva() {
    Cuadrilla cuadrilla = Cuadrilla.crear("  Cuadrilla Norte ", "   ");

    assertThat(cuadrilla.getNombre()).isEqualTo("Cuadrilla Norte");
    assertThat(cuadrilla.getZona()).isNull();
    assertThat(cuadrilla.isActiva()).isTrue();
  }

  @Test
  void modificar_nombreYZona_losCambiaNormalizados() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", null);

    cuadrilla.modificar(" Sur ", " Zona Sur ");

    assertThat(cuadrilla.getNombre()).isEqualTo("Sur");
    assertThat(cuadrilla.getZona()).isEqualTo("Zona Sur");
  }

  @Test
  void desactivar_sinTecnicosActivos_quedaInactiva() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", "Norte");

    cuadrilla.desactivar(0);

    assertThat(cuadrilla.isActiva()).isFalse();
  }

  @Test
  void desactivar_yaInactiva_sigueInactiva() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", "Norte");
    cuadrilla.desactivar(0);

    cuadrilla.desactivar(0);

    assertThat(cuadrilla.isActiva()).isFalse();
  }

  @Test
  void desactivar_conTecnicosActivos_lanzaReglaNegocioConLaCantidadYSigueActiva() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", "Norte");

    assertThatThrownBy(() -> cuadrilla.desactivar(3))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("tiene 3 técnicos activos")
        .hasMessageContaining("cambialos de cuadrilla");
    assertThat(cuadrilla.isActiva()).isTrue();
  }

  @Test
  void desactivar_conUnTecnicoActivo_usaElSingular() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", "Norte");

    assertThatThrownBy(() -> cuadrilla.desactivar(1))
        .hasMessageContaining("tiene 1 técnico activo.");
  }

  @Test
  void activar_cuadrillaInactiva_quedaActiva() {
    Cuadrilla cuadrilla = Cuadrilla.crear("Norte", "Norte");
    cuadrilla.desactivar(0);

    cuadrilla.activar();

    assertThat(cuadrilla.isActiva()).isTrue();
  }
}
