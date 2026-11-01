package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Grupo de técnicos que recibe las OTs (Fig. 7). La Jefatura las da de alta y las desactiva. */
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
    cuadrilla.modificar(nombre, zona);
    cuadrilla.activa = true;
    return cuadrilla;
  }

  /** El nombre se guarda sin espacios a los costados; una zona en blanco queda sin zona. */
  public void modificar(String nombre, String zona) {
    this.nombre = normalizarNombre(nombre);
    this.zona = zona == null || zona.isBlank() ? null : zona.trim();
  }

  public static String normalizarNombre(String nombre) {
    return Objects.requireNonNull(nombre).trim();
  }

  /**
   * Baja lógica. Una cuadrilla inactiva no puede tener técnicos activos: primero hay que cambiarlos
   * de cuadrilla.
   */
  public void desactivar(long tecnicosActivos) {
    if (tecnicosActivos > 0) {
      String tecnicos = tecnicosActivos == 1 ? " técnico activo" : " técnicos activos";
      throw new ReglaNegocioException(
          "La cuadrilla "
              + nombre
              + " tiene "
              + tecnicosActivos
              + tecnicos
              + ". Primero cambialos de cuadrilla.");
    }
    this.activa = false;
  }

  public void activar() {
    this.activa = true;
  }
}
