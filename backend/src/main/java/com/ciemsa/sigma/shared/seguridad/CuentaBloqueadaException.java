package com.ciemsa.sigma.shared.seguridad;

import java.time.Instant;
import lombok.Getter;
import org.springframework.security.authentication.LockedException;

/** Cuenta bloqueada por intentos fallidos (CU-12 4c), con la hora en que se libera. */
@Getter
public class CuentaBloqueadaException extends LockedException {

  private final Instant bloqueadoHasta;

  public CuentaBloqueadaException(Instant bloqueadoHasta) {
    super("Cuenta bloqueada hasta " + bloqueadoHasta);
    this.bloqueadoHasta = bloqueadoHasta;
  }
}
