package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CierreTecnicoTest {

  @Test
  void crear_recortaEspaciosYObservacionesEnBlancoQuedanNull() {
    CierreTecnico cierre = new CierreTecnico("  Lámpara quemada ", " Cambio ", "   ");

    assertThat(cierre.diagnostico()).isEqualTo("Lámpara quemada");
    assertThat(cierre.solucion()).isEqualTo("Cambio");
    assertThat(cierre.observaciones()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void crear_sinDiagnostico_lanzaReglaDeNegocio(String diagnostico) {
    assertThatThrownBy(() -> new CierreTecnico(diagnostico, "Cambio", null))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("diagnóstico");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  void crear_sinSolucion_lanzaReglaDeNegocio(String solucion) {
    assertThatThrownBy(() -> new CierreTecnico("Lámpara quemada", solucion, null))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("solución");
  }

  @Test
  void crear_textoMasLargoQueLaColumna_lanzaReglaDeNegocio() {
    String largo = "x".repeat(CierreTecnico.LARGO_MAXIMO + 1);

    assertThatThrownBy(() -> new CierreTecnico("Diagnóstico", "Solución", largo))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("observaciones");
    assertThat(new CierreTecnico("x".repeat(CierreTecnico.LARGO_MAXIMO), "S", null).diagnostico())
        .hasSize(CierreTecnico.LARGO_MAXIMO);
  }
}
