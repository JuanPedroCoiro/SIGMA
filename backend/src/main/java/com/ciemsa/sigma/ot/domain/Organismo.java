package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Organismo contratante (Fig. 7). Catálogo cargado por migración (RF-29). */
@Entity
@Table(name = "organismo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organismo extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_organismo")
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombre;
}
