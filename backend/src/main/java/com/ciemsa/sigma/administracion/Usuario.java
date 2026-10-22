package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.Rol;
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
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cuenta de acceso al sistema (CU-12, CU-13). El correo es el usuario de acceso. */
@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity {
  /** Intentos fallidos consecutivos que bloquean la cuenta (CU-12 4c). */
  public static final int MAX_INTENTOS_FALLIDOS = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_usuario")
  private Long id;

  @Column(nullable = false, length = 150)
  private String nombre;

  @Column(nullable = false, length = 255)
  private String correo;

  /** Hash BCrypt: la contraseña en texto plano nunca llega a la entidad. */
  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Rol rol;

  /** Solo los técnicos tienen cuadrilla (CU-13a). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "id_cuadrilla")
  private Cuadrilla cuadrilla;

  @Column(nullable = false)
  private boolean activo;

  @Column(nullable = false)
  private int intentosFallidos;

  /** Fin del bloqueo temporal por intentos fallidos (CU-12 4c); null si no está bloqueado. */
  private Instant bloqueadoHasta;

  /** Alta de una cuenta (CU-13a). Recibe la contraseña ya hasheada. */
  public static Usuario crear(
      String nombre, String correo, String passwordHash, Rol rol, Cuadrilla cuadrilla) {
    Usuario usuario = new Usuario();
    usuario.nombre = Objects.requireNonNull(nombre);
    usuario.correo = normalizarCorreo(correo);
    usuario.passwordHash = Objects.requireNonNull(passwordHash);
    usuario.asignarRol(rol, cuadrilla);
    usuario.activo = true;
    usuario.intentosFallidos = 0;
    return usuario;
  }

  /** El correo se guarda y se busca siempre en minúsculas y sin espacios. */
  public static String normalizarCorreo(String correo) {
    return Objects.requireNonNull(correo).trim().toLowerCase(Locale.ROOT);
  }

  /** Datos editables por la Jefatura (CU-13b). El cambio de rol rige desde el siguiente ingreso. */
  public void modificar(String nombre, String correo, Rol rol, Cuadrilla cuadrilla) {
    this.nombre = Objects.requireNonNull(nombre);
    this.correo = normalizarCorreo(correo);
    asignarRol(rol, cuadrilla);
  }

  /** La Jefatura asigna una contraseña nueva (CU-13b). También levanta un bloqueo vigente. */
  public void restablecerContrasena(String nuevoPasswordHash) {
    this.passwordHash = Objects.requireNonNull(nuevoPasswordHash);
    desbloquear();
  }

  /** Baja lógica (CU-13d): no puede ingresar, pero se conserva con su historial. */
  public void darDeBaja() {
    this.activo = false;
  }

  public boolean estaBloqueado(Instant ahora) {
    return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
  }

  /**
   * Suma un intento fallido; al llegar al máximo bloquea la cuenta durante {@code duracionBloqueo}
   * (CU-12 4c). Mientras está bloqueada, los intentos no cuentan.
   */
  public void registrarIntentoFallido(Instant ahora, Duration duracionBloqueo) {
    if (estaBloqueado(ahora)) {
      return;
    }
    intentosFallidos++;
    if (intentosFallidos >= MAX_INTENTOS_FALLIDOS) {
      bloqueadoHasta = ahora.plus(duracionBloqueo);
      intentosFallidos = 0;
    }
  }

  /** Un ingreso correcto corta la racha de intentos fallidos. */
  public void registrarIngresoExitoso() {
    desbloquear();
  }

  private void desbloquear() {
    this.intentosFallidos = 0;
    this.bloqueadoHasta = null;
  }

  /** Un técnico necesita una cuadrilla activa (4b); los demás roles no llevan cuadrilla. */
  private void asignarRol(Rol rol, Cuadrilla cuadrilla) {
    Objects.requireNonNull(rol);
    if (!rol.requiereCuadrilla()) {
      this.rol = rol;
      this.cuadrilla = null;
      return;
    }
    if (cuadrilla == null) {
      throw new ReglaNegocioException("Un técnico debe pertenecer a una cuadrilla.");
    }
    if (!cuadrilla.isActiva()) {
      throw new ReglaNegocioException("La cuadrilla " + cuadrilla.getNombre() + " está inactiva.");
    }
    this.rol = rol;
    this.cuadrilla = cuadrilla;
  }
}
