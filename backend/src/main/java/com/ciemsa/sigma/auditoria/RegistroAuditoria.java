package com.ciemsa.sigma.auditoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Una fila de la bitácora (Fig. 7, RF-21): quién hizo qué acción sobre qué entidad, cuándo, y el
 * valor anterior y nuevo en JSON. Solo se inserta: no extiende BaseEntity porque no tiene versión
 * ni fecha de modificación, y la base rechaza UPDATE y DELETE con un trigger (V9).
 */
@Entity
@Immutable
@Table(name = "registro_auditoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroAuditoria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_registro_auditoria")
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EntidadAuditada entidad;

  @Column(nullable = false)
  private Long idEntidad;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AccionAuditada accion;

  @Column(nullable = false)
  private Long idUsuario;

  @Column(nullable = false, length = 150)
  private String nombreUsuario;

  @Column(nullable = false)
  private Instant fechaHora;

  /** JSON; null cuando no aplica (por ejemplo, en un alta). */
  @JdbcTypeCode(SqlTypes.JSON)
  private String valorAnterior;

  @JdbcTypeCode(SqlTypes.JSON)
  private String valorNuevo;

  static RegistroAuditoria de(
      EntidadAuditada entidad,
      Long idEntidad,
      AccionAuditada accion,
      Long idUsuario,
      String nombreUsuario,
      Instant fechaHora,
      String valorAnterior,
      String valorNuevo) {
    RegistroAuditoria registro = new RegistroAuditoria();
    registro.entidad = Objects.requireNonNull(entidad);
    registro.idEntidad = Objects.requireNonNull(idEntidad);
    registro.accion = Objects.requireNonNull(accion);
    registro.idUsuario = Objects.requireNonNull(idUsuario);
    registro.nombreUsuario = Objects.requireNonNull(nombreUsuario);
    registro.fechaHora = Objects.requireNonNull(fechaHora);
    registro.valorAnterior = valorAnterior;
    registro.valorNuevo = valorNuevo;
    return registro;
  }
}
