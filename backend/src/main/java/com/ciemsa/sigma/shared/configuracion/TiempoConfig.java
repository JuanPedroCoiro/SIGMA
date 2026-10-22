package com.ciemsa.sigma.shared.configuracion;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Hora del sistema: un único Clock en UTC, usado también por la auditoría de las entidades. */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "proveedorFechaAuditoria")
public class TiempoConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  DateTimeProvider proveedorFechaAuditoria(Clock clock) {
    return () -> Optional.of(Instant.now(clock));
  }
}
