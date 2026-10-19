package com.ciemsa.sigma.shared.api;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de prueba: comprueba que frontend, backend y base están conectados. */
@RestController
@RequestMapping("/api/v1/ping")
class PingController {

  private final JdbcTemplate jdbc;

  PingController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  PingResponse ping() {
    String version = jdbc.queryForObject("select version()", String.class);
    return new PingResponse("ok", version, OffsetDateTime.now());
  }

  public record PingResponse(String estado, String baseDeDatos, OffsetDateTime hora) {}
}
