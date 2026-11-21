package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Una pausa de una OT, con su motivo y quién la abrió y la cerró. La maneja la OT. */
@Entity
@Table(name = "pausa_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PausaOT extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_pausa_ot")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "id_orden_trabajo")
  @Getter(AccessLevel.NONE)
  private OrdenTrabajo ordenTrabajo;

  @Column(nullable = false)
  private Instant inicio;

  /** Null mientras está abierta. */
  private Instant fin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MotivoPausa motivo;

  @Column(length = 500)
  private String detalle;

  /** Del módulo administracion: se referencia por id. */
  @Column(name = "id_usuario_pausa", nullable = false)
  private Long idUsuarioPausa;

  @Column(name = "id_usuario_reanudacion")
  private Long idUsuarioReanudacion;

  /** Solo la OT abre pausas. El detalle es obligatorio si el motivo lo requiere. */
  static PausaOT abrir(
      OrdenTrabajo ordenTrabajo,
      MotivoPausa motivo,
      String detalle,
      Long idUsuario,
      Instant ahora) {
    Objects.requireNonNull(motivo, "El motivo de la pausa es obligatorio.");
    String detalleLimpio = detalle == null || detalle.isBlank() ? null : detalle.strip();
    if (motivo.requiereDetalle() && detalleLimpio == null) {
      throw new ReglaNegocioException(
          motivo == MotivoPausa.TERCERO
              ? "Indicá qué tercero causa la pausa."
              : "Detallá el motivo de la pausa.");
    }
    PausaOT pausa = new PausaOT();
    pausa.ordenTrabajo = Objects.requireNonNull(ordenTrabajo);
    pausa.motivo = motivo;
    pausa.detalle = detalleLimpio;
    pausa.idUsuarioPausa = Objects.requireNonNull(idUsuario);
    pausa.inicio = Objects.requireNonNull(ahora);
    return pausa;
  }

  void cerrar(Long idUsuario, Instant ahora) {
    this.idUsuarioReanudacion = Objects.requireNonNull(idUsuario);
    this.fin = Objects.requireNonNull(ahora);
  }

  public boolean estaAbierta() {
    return fin == null;
  }

  /** Lo que duró; si sigue abierta, hasta {@code ahora}. */
  public Duration duracion(Instant ahora) {
    return Duration.between(inicio, estaAbierta() ? ahora : fin);
  }
}
