package com.ciemsa.sigma.administracion.dto;

import jakarta.validation.constraints.NotNull;

/** La cuadrilla a la que se suma el técnico (HU-20). No lleva usuario: siempre es el actual. */
public record CambiarCuadrillaRequest(@NotNull Long idCuadrilla) {}
