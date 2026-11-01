package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Plazos de un organismo para una prioridad (RN-02). Las reglas son datos (RNF-9): un organismo
 * nuevo es una fila, no código.
 */
@Entity
@Table(name = "regla_sla")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReglaSLA extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_regla_sla")
  private Long id;

  @Column(name = "id_organismo", nullable = false)
  private Long idOrganismo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Prioridad prioridad;

  @Column(nullable = false)
  private int horasRespuesta;

  @Column(nullable = false)
  private int horasResolucion;

  public static ReglaSLA crear(
      Long idOrganismo, Prioridad prioridad, int horasRespuesta, int horasResolucion) {
    ReglaSLA regla = new ReglaSLA();
    regla.idOrganismo = Objects.requireNonNull(idOrganismo);
    regla.prioridad = Objects.requireNonNull(prioridad);
    regla.horasRespuesta = horasRespuesta;
    regla.horasResolucion = horasResolucion;
    return regla;
  }

  /**
   * Fecha límite de la OT: inicio del SLA más las horas de resolución. Confirmar con CIEMSA si el
   * contrato la mide con las horas de resolución (supuesto actual) o con las de respuesta.
   */
  public Instant calcularVencimiento(Instant inicioSla) {
    return Objects.requireNonNull(inicioSla).plus(Duration.ofHours(horasResolucion));
  }
}
