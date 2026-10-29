package com.ciemsa.sigma.administracion;

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

/** Grupo de técnicos que recibe las OTs (Fig. 7). En el MVP se cargan por script. */
@Entity
@Table(name = "cuadrilla")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cuadrilla extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_cuadrilla")
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombre;

  @Column(length = 100)
  private String zona;

  @Column(nullable = false)
  private boolean activa;

  public static Cuadrilla crear(String nombre, String zona) {
    Cuadrilla cuadrilla = new Cuadrilla();
    cuadrilla.nombre = nombre;
    cuadrilla.zona = zona;
    cuadrilla.activa = true;
    return cuadrilla;
  }
}
