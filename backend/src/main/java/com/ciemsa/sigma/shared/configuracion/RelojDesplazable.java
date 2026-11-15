package com.ciemsa.sigma.shared.configuracion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reloj que se puede correr al pasado solo en el hilo actual, para generar los datos de la demo
 * (#81) con fechas relativas al arranque pasando por los servicios. Solo es el {@link Clock} de la
 * aplicación cuando {@link DatosDemoHabilitado}; si no, se usa el reloj de siempre. Fuera de {@link
 * #en} da la hora de su base.
 */
public final class RelojDesplazable extends Clock {

  private static final ThreadLocal<Duration> DESPLAZAMIENTO = new ThreadLocal<>();

  private final Clock base;

  public RelojDesplazable(Clock base) {
    this.base = Objects.requireNonNull(base);
  }

  /** Ejecuta la acción con el reloj de este hilo en {@code momento}; el tiempo sigue corriendo. */
  public <T> T en(Instant momento, Supplier<T> accion) {
    Duration anterior = DESPLAZAMIENTO.get();
    DESPLAZAMIENTO.set(Duration.between(base.instant(), momento));
    try {
      return accion.get();
    } finally {
      if (anterior == null) {
        DESPLAZAMIENTO.remove();
      } else {
        DESPLAZAMIENTO.set(anterior);
      }
    }
  }

  @Override
  public Instant instant() {
    Duration desplazamiento = DESPLAZAMIENTO.get();
    Instant ahora = base.instant();
    return desplazamiento == null ? ahora : ahora.plus(desplazamiento);
  }

  @Override
  public ZoneId getZone() {
    return base.getZone();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new RelojDesplazable(base.withZone(zone));
  }
}
