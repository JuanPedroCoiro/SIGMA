package com.ciemsa.sigma.ot.infrastructure;

import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Historial de la instalación (HU-10, CU-8): las OTs terminadas y los incidentes sin OT en una sola
 * línea de tiempo paginada. SQL nativo porque mezcla dos tablas en una página (UNION ALL), con
 * joins a cuadrilla y categoría para leer sus nombres sin pasar por otros módulos.
 *
 * <p>La fecha de la línea de tiempo es el cierre técnico ({@code cerrada_en}) en las cerradas y
 * validadas, {@code modificado_en} en las anuladas (estado final: ver EstadoOT.ANULADA) y el
 * reporte ({@code creado_en}) en los incidentes.
 */
@Repository
@RequiredArgsConstructor
public class HistorialInstalacionLectura {

  private static final String ORDENES =
      """
      SELECT 'OT' AS clase, o.id_orden_trabajo AS id, o.numero, o.estado, o.tipo,
             CASE WHEN o.estado = :anulada THEN o.modificado_en ELSE o.cerrada_en END AS fecha,
             COALESCE(i.creado_en, o.creado_en) AS reportado_en,
             o.iniciada_en, o.cerrada_en, o.id_cuadrilla, c.nombre AS cuadrilla,
             i.id_incidente, i.numero AS numero_incidente, cat.nombre AS categoria,
             i.prioridad, i.descripcion, o.diagnostico, o.solucion,
             o.observaciones_cierre AS observaciones, o.motivo_anulacion,
             CAST(NULL AS VARCHAR) AS tercero, CAST(NULL AS VARCHAR) AS causa
      FROM orden_trabajo o
          LEFT JOIN incidente i ON i.id_incidente = o.id_incidente
          LEFT JOIN categoria_falla cat ON cat.id_categoria_falla = i.id_categoria_falla
          LEFT JOIN cuadrilla c ON c.id_cuadrilla = o.id_cuadrilla
      WHERE o.id_instalacion = :idInstalacion AND o.estado IN (:estadosOt)
      """;

  private static final String INCIDENTES =
      """
      SELECT 'INCIDENTE', i.id_incidente, i.numero, i.estado, CAST(NULL AS VARCHAR),
             i.creado_en, i.creado_en,
             CAST(NULL AS TIMESTAMPTZ), CAST(NULL AS TIMESTAMPTZ), CAST(NULL AS BIGINT),
             CAST(NULL AS VARCHAR),
             i.id_incidente, i.numero, cat.nombre, i.prioridad, i.descripcion,
             CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR), CAST(NULL AS VARCHAR),
             CAST(NULL AS VARCHAR), i.tercero, i.causa
      FROM incidente i
          LEFT JOIN categoria_falla cat ON cat.id_categoria_falla = i.id_categoria_falla
      WHERE i.id_instalacion = :idInstalacion AND i.estado IN (:estadosIncidente)
      """;

  // A igual fecha, las OTs antes que los incidentes ('OT' > 'INCIDENTE') y la más nueva primero.
  private static final String PAGINA =
      "SELECT * FROM ("
          + ORDENES
          + " UNION ALL "
          + INCIDENTES
          + ") h ORDER BY h.fecha DESC, h.clase DESC, h.id DESC LIMIT :limite OFFSET :desde";

  private static final String TOTAL =
      """
      SELECT (SELECT count(*) FROM orden_trabajo o
              WHERE o.id_instalacion = :idInstalacion AND o.estado IN (:estadosOt))
           + (SELECT count(*) FROM incidente i
              WHERE i.id_instalacion = :idInstalacion AND i.estado IN (:estadosIncidente))
      """;

  private final EntityManager entityManager;

  /** La página pedida del historial de la instalación, de lo más nuevo a lo más viejo. */
  public Page<FilaHistorial> pagina(Long idInstalacion, Pageable pageable) {
    Query consulta = entityManager.createNativeQuery(PAGINA, Tuple.class);
    filtros(consulta, idInstalacion)
        .setParameter("anulada", EstadoOT.ANULADA.name())
        .setParameter("limite", pageable.getPageSize())
        .setParameter("desde", pageable.getOffset());
    @SuppressWarnings("unchecked")
    List<Tuple> tuplas = consulta.getResultList();

    long total =
        ((Number) filtros(entityManager.createNativeQuery(TOTAL), idInstalacion).getSingleResult())
            .longValue();
    return new PageImpl<>(
        tuplas.stream().map(HistorialInstalacionLectura::fila).toList(), pageable, total);
  }

  private static Query filtros(Query consulta, Long idInstalacion) {
    return consulta
        .setParameter("idInstalacion", idInstalacion)
        .setParameter("estadosOt", EstadoOT.ESTADOS_HISTORIAL.stream().map(Enum::name).toList())
        .setParameter(
            "estadosIncidente",
            EstadoIncidente.SIN_OT_EN_HISTORIAL.stream().map(Enum::name).toList());
  }

  private static FilaHistorial fila(Tuple t) {
    return new FilaHistorial(
        texto(t, "clase"),
        numero(t, "id"),
        texto(t, "numero"),
        texto(t, "estado"),
        texto(t, "tipo"),
        instante(t, "fecha"),
        instante(t, "reportado_en"),
        instante(t, "iniciada_en"),
        instante(t, "cerrada_en"),
        numero(t, "id_cuadrilla"),
        texto(t, "cuadrilla"),
        numero(t, "id_incidente"),
        texto(t, "numero_incidente"),
        texto(t, "categoria"),
        texto(t, "prioridad"),
        texto(t, "descripcion"),
        texto(t, "diagnostico"),
        texto(t, "solucion"),
        texto(t, "observaciones"),
        texto(t, "motivo_anulacion"),
        texto(t, "tercero"),
        texto(t, "causa"));
  }

  private static String texto(Tuple t, String columna) {
    Object valor = t.get(columna);
    return valor == null ? null : valor.toString();
  }

  private static Long numero(Tuple t, String columna) {
    Object valor = t.get(columna);
    return valor == null ? null : ((Number) valor).longValue();
  }

  /** El driver devuelve TIMESTAMPTZ como Instant u OffsetDateTime según la versión de Hibernate. */
  private static Instant instante(Tuple t, String columna) {
    Object valor = t.get(columna);
    return switch (valor) {
      case null -> null;
      case Instant instante -> instante;
      case OffsetDateTime fecha -> fecha.toInstant();
      case java.sql.Timestamp marca -> marca.toInstant();
      default -> throw new IllegalStateException("Fecha inesperada en " + columna + ": " + valor);
    };
  }
}
