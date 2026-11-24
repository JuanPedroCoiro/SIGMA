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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Archivo adjunto (Fig. 7): foto de un incidente (#71) o del cierre técnico de una OT (#90), nunca
 * de los dos. El contenido está en AlmacenArchivos; acá, sus datos y dónde encontrarlo.
 */
@Entity
@Table(name = "adjunto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Adjunto extends BaseEntity {

  private static final int LARGO_NOMBRE = 255;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_adjunto")
  private Long id;

  /** El incidente, si es una foto del incidente (#71); null si es del cierre de una OT. */
  @Column(name = "id_incidente")
  private Long idIncidente;

  /** La OT, si es una foto del cierre técnico (#90); null si es del incidente. */
  @Column(name = "id_orden_trabajo")
  private Long idOrdenTrabajo;

  /** Antes, Durante o Después: solo en las fotos del cierre (#90). */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private MomentoFoto momento;

  /** Solo para mostrar: el archivo se guarda con otro nombre. */
  @Column(name = "nombre_original", nullable = false, length = LARGO_NOMBRE)
  private String nombreOriginal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TipoImagen tipo;

  @Column(name = "tamanio_bytes", nullable = false)
  private long tamanioBytes;

  @Column(nullable = false, length = 255)
  private String ubicacion;

  /** Quién la subió. */
  @Column(name = "id_usuario", nullable = false)
  private Long idUsuario;

  public static Adjunto fotoDeIncidente(
      Long idIncidente,
      String nombreOriginal,
      TipoImagen tipo,
      long tamanioBytes,
      String ubicacion,
      Long idUsuario) {
    Adjunto adjunto = new Adjunto();
    adjunto.idIncidente = Objects.requireNonNull(idIncidente);
    adjunto.nombreOriginal = limpiarNombre(nombreOriginal);
    adjunto.tipo = Objects.requireNonNull(tipo);
    adjunto.tamanioBytes = tamanioBytes;
    adjunto.ubicacion = Objects.requireNonNull(ubicacion);
    adjunto.idUsuario = Objects.requireNonNull(idUsuario);
    return adjunto;
  }

  public static Adjunto fotoDeCierre(
      Long idOrdenTrabajo,
      MomentoFoto momento,
      String nombreOriginal,
      TipoImagen tipo,
      long tamanioBytes,
      String ubicacion,
      Long idUsuario) {
    Adjunto adjunto = new Adjunto();
    adjunto.idOrdenTrabajo = Objects.requireNonNull(idOrdenTrabajo);
    adjunto.momento = Objects.requireNonNull(momento);
    adjunto.nombreOriginal = limpiarNombre(nombreOriginal);
    adjunto.tipo = Objects.requireNonNull(tipo);
    adjunto.tamanioBytes = tamanioBytes;
    adjunto.ubicacion = Objects.requireNonNull(ubicacion);
    adjunto.idUsuario = Objects.requireNonNull(idUsuario);
    return adjunto;
  }

  /** Solo el nombre, sin carpetas, y hasta 255 caracteres; si no vino, «foto». */
  private static String limpiarNombre(String nombre) {
    if (nombre == null) {
      return "foto";
    }
    int barra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
    String soloNombre = nombre.substring(barra + 1).strip();
    if (soloNombre.isEmpty()) {
      return "foto";
    }
    return soloNombre.length() > LARGO_NOMBRE ? soloNombre.substring(0, LARGO_NOMBRE) : soloNombre;
  }
}
