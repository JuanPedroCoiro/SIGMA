package com.ciemsa.sigma.administracion.dto;

import java.util.List;

/**
 * La cuadrilla del técnico y a cuáles se puede sumar (HU-20). {@code otsAbiertas} son las que deja
 * de ver en «Mis OTs» si se cambia (asignadas, en proceso y en pausa), y {@code otsEnCurso},
 * cuántas de ellas ya empezó la cuadrilla (en proceso o en pausa). {@code actual} es null si no
 * tiene cuadrilla.
 */
public record MiCuadrillaResponse(
    CuadrillaResumen actual, long otsAbiertas, long otsEnCurso, List<CuadrillaOpcion> activas) {}
