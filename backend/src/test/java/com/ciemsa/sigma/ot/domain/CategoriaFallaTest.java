package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class CategoriaFallaTest {

  @Test
  void validarDescripcion_otraConDetalleCorto_lanzaReglaNegocio() {
    assertThatThrownBy(() -> categoria("Otra").validarDescripcion("  Falla  "))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("10 caracteres");
  }

  @Test
  void validarDescripcion_otraConDetalle_esValida() {
    assertThatCode(() -> categoria("Otra").validarDescripcion("Cable cortado en el poste"))
        .doesNotThrowAnyException();
  }

  @Test
  void validarDescripcion_otraCategoriaConDescripcionCorta_esValida() {
    assertThatCode(() -> categoria("Lámpara quemada").validarDescripcion("Roja"))
        .doesNotThrowAnyException();
  }

  /** Solo la carga la base: se arma con reflexión. */
  static CategoriaFalla categoria(String nombre) {
    CategoriaFalla categoria = BeanUtils.instantiateClass(CategoriaFalla.class);
    ReflectionTestUtils.setField(categoria, "nombre", nombre);
    return categoria;
  }
}
