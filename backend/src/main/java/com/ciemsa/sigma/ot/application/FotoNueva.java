package com.ciemsa.sigma.ot.application;

/** Una foto recién subida, todavía sin validar. Sin MultipartFile: la capa web queda en api. */
public record FotoNueva(String nombreOriginal, byte[] contenido) {}
