package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Período en que una cuadrilla tuvo la OT a cargo (HU-05). Es la base de «liberar y tomar» (HU-20).
 * La maneja la OT.
 */
@Entity
@Table(name = "tramo_cuadrilla_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TramoCuadrillaOT extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_tramo_cuadrilla_ot")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_orden_trabajo")
  @Getter(AccessLevel.NONE)
  private OrdenTrabajo ordenTrabajo;

  /** Del módulo administracion: se referencia por id. */
  @Column(name = "id_cuadrilla", nullable = false)
  private Long idCuadrilla;

  @Column(nullable = false)
  private Instant desde;

  /** Null mientras la cuadrilla tiene la OT a cargo. */
  private Instant hasta;

  /** Solo la OT abre tramos. */
  static TramoCuadrillaOT abrir(OrdenTrabajo ordenTrabajo, Long idCuadrilla, Instant ahora) {
    TramoCuadrillaOT tramo = new TramoCuadrillaOT();
    tramo.ordenTrabajo = Objects.requireNonNull(ordenTrabajo);
    tramo.idCuadrilla = Objects.requireNonNull(idCuadrilla);
    tramo.desde = Objects.requireNonNull(ahora);
    return tramo;
  }

  void cerrar(Instant ahora) {
    this.hasta = Objects.requireNonNull(ahora);
  }

  public boolean estaAbierto() {
    return hasta == null;
  }
}
