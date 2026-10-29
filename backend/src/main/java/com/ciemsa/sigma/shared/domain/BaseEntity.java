package com.ciemsa.sigma.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Columnas comunes a todas las tablas: fechas de creación y modificación, y versión para el bloqueo
 * optimista. Cada entidad declara su propia clave id_<entidad>.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  @CreatedDate
  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  @LastModifiedDate
  @Column(name = "modificado_en", nullable = false)
  private Instant modificadoEn;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;
}
