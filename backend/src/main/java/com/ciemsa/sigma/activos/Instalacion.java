package com.ciemsa.sigma.activos;

import com.ciemsa.sigma.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cruce o punto con equipos a mantener (Fig. 7). En el MVP se importa con los datos mínimos
 * (RF-29); tipo y dirección se completan después.
 */
@Entity
@Table(name = "instalacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instalacion extends BaseEntity {

  /** Rectángulo que contiene a Uruguay, con margen: descarta coordenadas mal cargadas. */
  private static final double LATITUD_MIN = -35.2;

  private static final double LATITUD_MAX = -30.0;
  private static final double LONGITUD_MIN = -58.6;
  private static final double LONGITUD_MAX = -53.0;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_instalacion")
  private Long id;

  @Column(nullable = false, length = 30)
  private String codigo;

  @Column(nullable = false, length = 150)
  private String nombre;

  @Column(length = 50)
  private String tipo;

  @Column(length = 200)
  private String direccion;

  @Column(name = "ruta_zona", nullable = false, length = 100)
  private String rutaZona;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal latitud;

  @Column(nullable = false, precision = 9, scale = 6)
  private BigDecimal longitud;

  /** Del módulo ot: se referencia por id. */
  @Column(name = "id_organismo", nullable = false)
  private Long idOrganismo;

  @Column(nullable = false)
  private boolean activa;

  public static Instalacion crear(
      String codigo,
      String nombre,
      String rutaZona,
      BigDecimal latitud,
      BigDecimal longitud,
      Long idOrganismo) {
    Instalacion instalacion = new Instalacion();
    instalacion.codigo = codigo;
    instalacion.nombre = nombre;
    instalacion.rutaZona = rutaZona;
    instalacion.latitud = latitud;
    instalacion.longitud = longitud;
    instalacion.idOrganismo = idOrganismo;
    instalacion.activa = true;
    return instalacion;
  }

  public static boolean dentroDeUruguay(BigDecimal latitud, BigDecimal longitud) {
    double lat = latitud.doubleValue();
    double lon = longitud.doubleValue();
    return lat >= LATITUD_MIN && lat <= LATITUD_MAX && lon >= LONGITUD_MIN && lon <= LONGITUD_MAX;
  }
}
