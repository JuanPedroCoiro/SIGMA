package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/**
 * Falla reportada en una instalación (CU-1, Fig. 7). Nace ABIERTO y genera su OT correctiva, salvo
 * que sea una falla de tercero (4b).
 */
@Entity
@Table(name = "incidente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Incidente extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_incidente")
  private Long id;

  /** INC-nnnn: lo asigna la base con una secuencia. */
  @Generated
  @Column(insertable = false, updatable = false, length = 20)
  private String numero;

  /** Del módulo activos: se referencia por id. */
  @Column(name = "id_instalacion", nullable = false)
  private Long idInstalacion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrigenIncidente origen;

  @Column(name = "id_categoria_falla", nullable = false)
  private Long idCategoriaFalla;

  /** La propone la categoría y el actor la confirma (RN-01). */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Prioridad prioridad;

  @Column(nullable = false, length = 2000)
  private String descripcion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoIncidente estado;

  @Column(length = 150)
  private String tercero;

  @Column(length = 500)
  private String causa;

  /** Quién lo registró. */
  @Column(name = "id_usuario", nullable = false)
  private Long idUsuario;

  public static Incidente registrar(
      Long idInstalacion,
      OrigenIncidente origen,
      Long idCategoriaFalla,
      Prioridad prioridad,
      String descripcion,
      Long idUsuario) {
    Incidente incidente = new Incidente();
    incidente.idInstalacion = Objects.requireNonNull(idInstalacion);
    incidente.origen = Objects.requireNonNull(origen);
    incidente.idCategoriaFalla = Objects.requireNonNull(idCategoriaFalla);
    incidente.prioridad = Objects.requireNonNull(prioridad);
    incidente.descripcion = Objects.requireNonNull(descripcion);
    incidente.idUsuario = Objects.requireNonNull(idUsuario);
    incidente.estado = EstadoIncidente.ABIERTO;
    return incidente;
  }

  /**
   * Genera la OT correctiva PENDIENTE (CU-1), al registrarlo o al resolver la espera de un tercero
   * (el tercero no volvió a comunicar). El SLA arranca en {@code inicioSla}; sin regla para el
   * organismo y la prioridad, la fecha límite queda null («SLA sin definir», 8a).
   */
  public OrdenTrabajo generarOrdenCorrectiva(Instant inicioSla, ReglaSLA regla) {
    exigirEstado(EstadoIncidente.ABIERTO, EstadoIncidente.EN_ESPERA_TERCERO);
    if (regla != null && regla.getPrioridad() != prioridad) {
      throw new IllegalArgumentException(
          "La regla de SLA es de prioridad " + regla.getPrioridad() + ", no " + prioridad + ".");
    }
    Instant fechaLimite = regla == null ? null : regla.calcularVencimiento(inicioSla);
    this.estado = EstadoIncidente.CON_OT;
    return OrdenTrabajo.correctiva(this, inicioSla, fechaLimite);
  }

  /** Falla de un tercero (CU-1 4b): queda en espera, sin OT ni SLA. */
  public void ponerEnEsperaDeTercero(String tercero, String causa) {
    exigirEstado(EstadoIncidente.ABIERTO);
    if (tercero == null || tercero.isBlank()) {
      throw new ReglaNegocioException("Indicá el tercero responsable de la falla.");
    }
    this.tercero = tercero.trim();
    this.causa = causa == null || causa.isBlank() ? null : causa.trim();
    this.estado = EstadoIncidente.EN_ESPERA_TERCERO;
  }

  /** Resuelve la espera sin OT: el tercero volvió a comunicar y la falla se resolvió (4b). */
  public void cerrarSinOt() {
    exigirEstado(EstadoIncidente.EN_ESPERA_TERCERO);
    this.estado = EstadoIncidente.CERRADO_SIN_OT;
  }

  public boolean estaEnEsperaDeTercero() {
    return estado == EstadoIncidente.EN_ESPERA_TERCERO;
  }

  private void exigirEstado(EstadoIncidente... permitidos) {
    if (!Arrays.asList(permitidos).contains(estado)) {
      throw new ReglaNegocioException(
          "No se puede realizar la operación: el incidente está en estado " + estado + ".");
    }
  }
}
