package com.ciemsa.sigma.ot.api;

import com.ciemsa.sigma.ot.domain.CierreTecnico;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cierre técnico (CU-3, pasos 6 a 10). {@code idOperacion} lo genera el celular una vez por cierre
 * y lo repite en cada reintento: si ya se procesó, el backend devuelve el resultado anterior (R8).
 */
public record CerrarOtRequest(
    @NotNull(message = "Falta el identificador de la operación.") UUID idOperacion,
    @NotBlank(message = "Indicá el diagnóstico.")
        @Size(max = 2000, message = "El diagnóstico admite hasta 2000 caracteres.")
        String diagnostico,
    @NotBlank(message = "Indicá la solución.")
        @Size(max = 2000, message = "La solución admite hasta 2000 caracteres.")
        String solucion,
    @Size(max = 2000, message = "Las observaciones admiten hasta 2000 caracteres.")
        String observaciones) {

  CierreTecnico cierre() {
    return new CierreTecnico(diagnostico, solucion, observaciones);
  }
}
