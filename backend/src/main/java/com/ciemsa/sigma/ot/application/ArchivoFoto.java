package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.Adjunto;

/** Una foto con su contenido, para devolver la imagen. */
public record ArchivoFoto(Adjunto adjunto, byte[] contenido) {}
