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
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/**
 * Orden de trabajo (Fig. 7). Las operaciones delegan en su {@link EstadoOT} (patrón State): solo el
 * estado la cambia. Cada cambio queda como evento pendiente hasta que el servicio lo publica.
 */
@Entity
@Table(name = "orden_trabajo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdenTrabajo extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_orden_trabajo")
  private Long id;

  /** OT-nnnn: lo asigna la base con una secuencia. */
  @Generated
  @Column(insertable = false, updatable = false, length = 20)
  private String numero;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TipoOT tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoOT estado;

  /** Las preventivas no tienen incidente. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_incidente")
  private Incidente incidente;

  /** Del módulo activos: se referencia por id. */
  @Column(name = "id_instalacion", nullable = false)
  private Long idInstalacion;

  /** Del módulo administracion: se referencia por id. */
  @Column(name = "id_cuadrilla")
  private Long idCuadrilla;

  private LocalDate fechaEjecucion;

  private Instant inicioSla;

  /** Null = «SLA sin definir» (no hay regla) o preventiva. */
  private Instant fechaLimite;

  /** Estado al que vuelve al reanudar; solo en EN_PAUSA. */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private EstadoOT estadoAnteriorPausa;

  @Column(length = 500)
  private String motivoAnulacion;

  @Transient
  @Getter(AccessLevel.NONE)
  private final List<CambioPendiente> cambiosPendientes = new ArrayList<>();

  /** OT correctiva de un incidente: nace PENDIENTE con su vencimiento (CU-1). */
  static OrdenTrabajo correctiva(Incidente incidente, Instant inicioSla, Instant fechaLimite) {
    OrdenTrabajo ot = new OrdenTrabajo();
    ot.tipo = TipoOT.CORRECTIVA;
    ot.estado = EstadoOT.PENDIENTE;
    ot.incidente = Objects.requireNonNull(incidente);
    ot.idInstalacion = incidente.getIdInstalacion();
    ot.inicioSla = Objects.requireNonNull(inicioSla);
    ot.fechaLimite = fechaLimite;
    ot.cambiosPendientes.add(new CambioPendiente(null, EstadoOT.PENDIENTE, inicioSla));
    return ot;
  }

  /** OT preventiva: sin incidente ni SLA. */
  public static OrdenTrabajo preventiva(
      Long idInstalacion, LocalDate fechaEjecucion, Instant ahora) {
    OrdenTrabajo ot = new OrdenTrabajo();
    ot.tipo = TipoOT.PREVENTIVA;
    ot.estado = EstadoOT.PENDIENTE;
    ot.idInstalacion = Objects.requireNonNull(idInstalacion);
    ot.fechaEjecucion = fechaEjecucion;
    ot.cambiosPendientes.add(
        new CambioPendiente(null, EstadoOT.PENDIENTE, Objects.requireNonNull(ahora)));
    return ot;
  }

  /** Condición derivada, no un estado: tiene fecha límite, sigue abierta y ya la pasó. */
  public boolean estaVencida(Instant ahora) {
    return fechaLimite != null && estado.estaAbierta() && ahora.isAfter(fechaLimite);
  }

  // Operaciones (Fig. 7 y Fig. 18): delegan en el estado

  /** Asignar, tomar o reasignar. */
  public void asignar(Long idCuadrilla, LocalDate fechaEjecucion, Instant ahora) {
    estado.asignar(this, idCuadrilla, fechaEjecucion, ahora);
  }

  public void liberar(Instant ahora) {
    estado.liberar(this, ahora);
  }

  public void iniciar(Instant ahora) {
    estado.iniciar(this, ahora);
  }

  public void pausar(Instant ahora) {
    estado.pausar(this, ahora);
  }

  public void reanudar(Instant ahora) {
    estado.reanudar(this, ahora);
  }

  public void cerrarTecnicamente(Instant ahora) {
    estado.cerrarTecnicamente(this, ahora);
  }

  public void validar(Instant ahora) {
    estado.validar(this, ahora);
  }

  public void observar(Instant ahora) {
    estado.observar(this, ahora);
  }

  public void anular(String motivo, Instant ahora) {
    estado.anular(this, motivo, ahora);
  }

  /**
   * Devuelve los eventos de dominio pendientes ({@link OrdenGenerada}, {@link EstadoOrdenCambiado})
   * y los descarta. Se llama después de guardar, porque los eventos llevan el id de la OT.
   */
  public List<Object> extraerEventos() {
    if (id == null) {
      throw new IllegalStateException("La OT debe guardarse antes de extraer sus eventos.");
    }
    List<Object> eventos = cambiosPendientes.stream().map(this::aEvento).toList();
    cambiosPendientes.clear();
    return eventos;
  }

  private Object aEvento(CambioPendiente cambio) {
    if (cambio.anterior() == null) {
      Long idIncidente = incidente == null ? null : incidente.getId();
      return new OrdenGenerada(id, idIncidente, cambio.ocurridoEn());
    }
    return new EstadoOrdenCambiado(id, cambio.anterior(), cambio.nuevo(), cambio.ocurridoEn());
  }

  // Solo las usa EstadoOT

  void registrarAsignacion(Long idCuadrilla, LocalDate fechaEjecucion) {
    this.idCuadrilla = Objects.requireNonNull(idCuadrilla);
    this.fechaEjecucion = fechaEjecucion;
  }

  void quitarCuadrilla() {
    this.idCuadrilla = null;
  }

  void abrirPausa() {
    this.estadoAnteriorPausa = estado;
  }

  void cerrarPausa() {
    this.estadoAnteriorPausa = null;
  }

  void registrarAnulacion(String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaNegocioException("El motivo de anulación es obligatorio.");
    }
    this.motivoAnulacion = motivo.trim();
  }

  void cambiarEstado(EstadoOT nuevo, Instant ahora) {
    cambiosPendientes.add(new CambioPendiente(estado, nuevo, Objects.requireNonNull(ahora)));
    this.estado = nuevo;
  }

  /** Cambio todavía sin publicar; anterior null = la OT recién generada. */
  private record CambioPendiente(EstadoOT anterior, EstadoOT nuevo, Instant ocurridoEn) {}
}
