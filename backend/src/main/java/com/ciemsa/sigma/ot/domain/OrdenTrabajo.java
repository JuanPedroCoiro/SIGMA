package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

  /** Desde cuándo corre el SLA; se mueve al reanudar tras una pausa por un tercero. */
  private Instant inicioSla;

  /** Null = «SLA sin definir» (no hay regla) o preventiva. */
  private Instant fechaLimite;

  /** Estado al que vuelve al reanudar; solo en EN_PAUSA. */
  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private EstadoOT estadoAnteriorPausa;

  @Column(length = 500)
  private String motivoAnulacion;

  /** Historial de pausas; a lo sumo una abierta (uq_pausa_ot_abierta). */
  @OneToMany(mappedBy = "ordenTrabajo", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("inicio")
  @Getter(AccessLevel.NONE)
  private final List<PausaOT> pausas = new ArrayList<>();

  @Transient
  @Getter(AccessLevel.NONE)
  private final List<Pendiente> pendientes = new ArrayList<>();

  /** OT correctiva de un incidente: nace PENDIENTE con su vencimiento (CU-1). */
  static OrdenTrabajo correctiva(Incidente incidente, Instant inicioSla, Instant fechaLimite) {
    OrdenTrabajo ot = new OrdenTrabajo();
    ot.tipo = TipoOT.CORRECTIVA;
    ot.estado = EstadoOT.PENDIENTE;
    ot.incidente = Objects.requireNonNull(incidente);
    ot.idInstalacion = incidente.getIdInstalacion();
    ot.inicioSla = Objects.requireNonNull(inicioSla);
    ot.fechaLimite = fechaLimite;
    ot.pendientes.add(new CambioPendiente(null, EstadoOT.PENDIENTE, inicioSla));
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
    ot.pendientes.add(new CambioPendiente(null, EstadoOT.PENDIENTE, Objects.requireNonNull(ahora)));
    return ot;
  }

  /**
   * Condición derivada, no un estado: tiene fecha límite, sigue abierta y ya la pasó. En pausa por
   * un tercero no vence, porque el SLA se reinicia al reanudar.
   */
  public boolean estaVencida(Instant ahora) {
    if (pausaAbierta().map(p -> p.getMotivo().reiniciaSla()).orElse(false)) {
      return false;
    }
    return fechaLimite != null && estado.estaAbierta() && ahora.isAfter(fechaLimite);
  }

  /** La pausa en curso, si está EN_PAUSA. */
  public Optional<PausaOT> pausaAbierta() {
    return pausas.stream().filter(PausaOT::estaAbierta).findFirst();
  }

  /** Las pausas de la OT, de la más vieja a la más nueva. */
  public List<PausaOT> getPausas() {
    return Collections.unmodifiableList(pausas);
  }

  /**
   * Tiempos de la OT hasta {@code ahora}: total desde que se generó, en pausa por motivo (la
   * abierta cuenta hasta ahora) y efectivo. Null si ya no está abierta: no se guarda cuándo se
   * cerró y el total crecería para siempre (pendiente para los informes de tiempos).
   */
  public TiemposOT tiempos(Instant ahora) {
    if (!estado.estaAbierta()) {
      return null;
    }
    Instant generada =
        Objects.requireNonNull(getCreadoEn(), "La OT debe guardarse antes de medir sus tiempos.");
    Duration total = Duration.between(generada, ahora);
    Map<MotivoPausa, Duration> enPausa = new EnumMap<>(MotivoPausa.class);
    for (PausaOT pausa : pausas) {
      enPausa.merge(pausa.getMotivo(), pausa.duracion(ahora), Duration::plus);
    }
    Duration pausado = enPausa.values().stream().reduce(Duration.ZERO, Duration::plus);
    return new TiemposOT(total, Collections.unmodifiableMap(enPausa), total.minus(pausado));
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

  /** Pausa con motivo; el detalle es obligatorio con TERCERO y OTRO. */
  public void pausar(MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
    estado.pausar(this, motivo, detalle, idUsuario, ahora);
    PausaOT pausa = pausaAbierta().orElseThrow();
    pendientes.add(new PausaPendiente(pausa.getMotivo(), pausa.getDetalle(), idUsuario, ahora));
  }

  /** Vuelve al estado anterior a la pausa; tras un tercero, el SLA arranca completo. */
  public void reanudar(Long idUsuario, Instant ahora) {
    Optional<PausaOT> pausa = pausaAbierta();
    estado.reanudar(this, idUsuario, ahora);
    pendientes.add(
        new ReanudacionPendiente(pausa.orElseThrow().getMotivo(), idUsuario, ahora, fechaLimite));
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

  /** Si estaba en pausa, la cierra con quien anula. */
  public void anular(String motivo, Long idUsuario, Instant ahora) {
    estado.anular(this, motivo, idUsuario, ahora);
  }

  /**
   * Devuelve los eventos de dominio pendientes ({@link OrdenGenerada}, {@link EstadoOrdenCambiado},
   * {@link OrdenPausada}, {@link OrdenReanudada}) en el orden en que ocurrieron y los descarta. Se
   * llama después de guardar, porque los eventos llevan el id de la OT.
   */
  public List<Object> extraerEventos() {
    if (id == null) {
      throw new IllegalStateException("La OT debe guardarse antes de extraer sus eventos.");
    }
    List<Object> eventos = pendientes.stream().map(this::aEvento).toList();
    pendientes.clear();
    return eventos;
  }

  private Object aEvento(Pendiente pendiente) {
    return switch (pendiente) {
      case CambioPendiente c when c.anterior() == null ->
          new OrdenGenerada(id, incidente == null ? null : incidente.getId(), c.ocurridoEn());
      case CambioPendiente c ->
          new EstadoOrdenCambiado(id, c.anterior(), c.nuevo(), c.ocurridoEn());
      case PausaPendiente p ->
          new OrdenPausada(id, p.motivo(), p.detalle(), p.idUsuario(), p.ocurridoEn());
      case ReanudacionPendiente r ->
          new OrdenReanudada(id, r.motivo(), r.idUsuario(), r.ocurridoEn(), r.fechaLimite());
    };
  }

  // Solo las usa EstadoOT

  void registrarAsignacion(Long idCuadrilla, LocalDate fechaEjecucion) {
    this.idCuadrilla = Objects.requireNonNull(idCuadrilla);
    this.fechaEjecucion = fechaEjecucion;
  }

  void quitarCuadrilla() {
    this.idCuadrilla = null;
  }

  /** Abre la pausa; la valida (motivo y detalle) antes de cambiar nada. */
  void abrirPausa(MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
    PausaOT pausa = PausaOT.abrir(this, motivo, detalle, idUsuario, ahora);
    pausas.add(pausa);
    this.estadoAnteriorPausa = estado;
  }

  /**
   * Cierra la pausa abierta. Al reanudar tras un tercero, el SLA arranca completo: se conserva el
   * plazo (fecha límite menos inicio) y se cuenta desde ahora. Sin fecha límite queda sin definir.
   */
  void cerrarPausa(Long idUsuario, Instant ahora, boolean alReanudar) {
    PausaOT pausa = pausaAbierta().orElseThrow();
    pausa.cerrar(idUsuario, ahora);
    this.estadoAnteriorPausa = null;
    if (alReanudar && pausa.getMotivo().reiniciaSla() && fechaLimite != null) {
      Duration plazo = Duration.between(inicioSla, fechaLimite);
      this.inicioSla = ahora;
      this.fechaLimite = ahora.plus(plazo);
    }
  }

  void registrarAnulacion(String motivo) {
    if (motivo == null || motivo.isBlank()) {
      throw new ReglaNegocioException("El motivo de anulación es obligatorio.");
    }
    this.motivoAnulacion = motivo.trim();
  }

  void cambiarEstado(EstadoOT nuevo, Instant ahora) {
    pendientes.add(new CambioPendiente(estado, nuevo, Objects.requireNonNull(ahora)));
    this.estado = nuevo;
  }

  /** Evento todavía sin publicar: le falta el id de la OT, que se conoce al guardar. */
  private sealed interface Pendiente
      permits CambioPendiente, PausaPendiente, ReanudacionPendiente {}

  /** Cambio de estado; anterior null = la OT recién generada. */
  private record CambioPendiente(EstadoOT anterior, EstadoOT nuevo, Instant ocurridoEn)
      implements Pendiente {}

  private record PausaPendiente(
      MotivoPausa motivo, String detalle, Long idUsuario, Instant ocurridoEn)
      implements Pendiente {}

  /** {@code fechaLimite} es la vigente después de reanudar. */
  private record ReanudacionPendiente(
      MotivoPausa motivo, Long idUsuario, Instant ocurridoEn, Instant fechaLimite)
      implements Pendiente {}
}
