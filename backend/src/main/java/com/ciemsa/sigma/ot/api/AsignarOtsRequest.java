package com.ciemsa.sigma.ot.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Asignación de una o más OTs a una cuadrilla con fecha de ejecución (CU-2, HU-05). */
public record AsignarOtsRequest(
    @NotEmpty(message = "Elegí al menos una OT.")
        @Size(max = 100, message = "Se pueden asignar hasta 100 OTs por vez.")
        List<@NotNull(message = "Hay una OT sin identificar.") Long> idsOrdenTrabajo,
    @NotNull(message = "Elegí la cuadrilla.") Long idCuadrilla,
    @NotNull(message = "Elegí la fecha de ejecución.") LocalDate fechaEjecucion) {}
