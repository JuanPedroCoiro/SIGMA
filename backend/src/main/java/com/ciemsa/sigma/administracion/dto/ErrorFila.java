package com.ciemsa.sigma.administracion.dto;

/** Error de una fila de la planilla; fila es la línea del archivo, contando el encabezado. */
public record ErrorFila(int fila, String campo, String mensaje) {}
