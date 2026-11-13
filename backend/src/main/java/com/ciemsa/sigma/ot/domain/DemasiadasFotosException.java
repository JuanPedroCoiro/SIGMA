package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;

/** El incidente pasaría el máximo de fotos (#71) (400). */
public class DemasiadasFotosException extends ReglaNegocioException {

  public DemasiadasFotosException(int maximo, long yaTiene) {
    super(
        "demasiadas-fotos",
        "Un incidente admite hasta "
            + maximo
            + " fotos y ya tiene "
            + yaTiene
            + ". Quitá alguna de las nuevas.");
  }
}
