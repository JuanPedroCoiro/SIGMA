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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tipo de falla de un incidente (Fig. 7). Propone la prioridad, que el actor confirma (RN-01).
 * Catálogo de solo lectura por ahora: lo carga la base.
 */
@Entity
@Table(name = "categoria_falla")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoriaFalla extends BaseEntity {

  /** La categoría genérica (CU-1 4a): la descripción tiene que traer el detalle. */
  public static final String OTRA = "Otra";

  /** Mínimo de caracteres de la descripción con la categoría «Otra». */
  public static final int MINIMO_DETALLE_OTRA = 10;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_categoria_falla")
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombre;

  @Enumerated(EnumType.STRING)
  @Column(name = "prioridad_sugerida", nullable = false, length = 20)
  private Prioridad prioridadSugerida;

  @Column(nullable = false)
  private boolean activa;

  public boolean esOtra() {
    return OTRA.equalsIgnoreCase(nombre);
  }

  /** 4a: con «Otra», la descripción es el único dato de qué falló y tiene que detallarlo. */
  public void validarDescripcion(String descripcion) {
    if (esOtra() && (descripcion == null || descripcion.strip().length() < MINIMO_DETALLE_OTRA)) {
      throw new ReglaNegocioException(
          "Con la categoría «Otra», detallá la falla en la descripción (al menos "
              + MINIMO_DETALLE_OTRA
              + " caracteres).");
    }
  }
}
