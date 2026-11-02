package com.ciemsa.sigma.shared.configuracion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Hora del sistema: un único Clock en UTC, usado también por la auditoría de las entidades. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "proveedorFechaAuditoria")
public class TiempoConfig {

  /**
   * Avanza de a microsegundos, la precisión de TIMESTAMPTZ: lo que se guarda es exactamente lo que
   * se devuelve, sin truncar a mano en cada servicio.
   */
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
  }

  @Bean
  DateTimeProvider proveedorFechaAuditoria(Clock clock) {
    return () -> Optional.of(Instant.now(clock));
  }
}
