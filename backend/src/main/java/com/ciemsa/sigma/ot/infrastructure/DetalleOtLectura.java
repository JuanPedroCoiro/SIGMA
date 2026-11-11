package com.ciemsa.sigma.ot.infrastructure;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Consultas de lectura para el detalle de la OT (#57): resuelve con joins los nombres de la
 * instalación, la cuadrilla, el incidente y los usuarios de las pausas, sin pasar por los servicios
 * de activos ni de administracion.
 */
@Repository
@RequiredArgsConstructor
public class DetalleOtLectura {

  private final EntityManager entityManager;

  /** Una fila por OT de {@code idsOrdenTrabajo}, indexada por su id. */
  public Map<Long, FilaDetalleOt> filas(Collection<Long> idsOrdenTrabajo) {
    if (idsOrdenTrabajo.isEmpty()) {
      return Map.of();
    }
    return entityManager
        .createQuery(
            """
        select new com.ciemsa.sigma.ot.infrastructure.FilaDetalleOt(
            o.id, ins.id, ins.codigo, ins.nombre, c.id, c.nombre,
            inc.id, inc.numero, inc.origen, cat.nombre, inc.prioridad,
            inc.descripcion, inc.tercero, inc.causa)
        from OrdenTrabajo o
            join Instalacion ins on ins.id = o.idInstalacion
            left join Cuadrilla c on c.id = o.idCuadrilla
            left join o.incidente inc
            left join CategoriaFalla cat on cat.id = inc.idCategoriaFalla
        where o.id in :ids""",
            FilaDetalleOt.class)
        .setParameter("ids", idsOrdenTrabajo)
        .getResultList()
        .stream()
        .collect(Collectors.toMap(FilaDetalleOt::idOrdenTrabajo, Function.identity()));
  }

  /** Nombre de cada usuario de {@code idsUsuario}, indexado por su id. */
  public Map<Long, String> nombresDeUsuarios(Collection<Long> idsUsuario) {
    if (idsUsuario.isEmpty()) {
      return Map.of();
    }
    List<Object[]> filas =
        entityManager
            .createQuery("select u.id, u.nombre from Usuario u where u.id in :ids", Object[].class)
            .setParameter("ids", idsUsuario)
            .getResultList();
    return filas.stream().collect(Collectors.toMap(f -> (Long) f[0], f -> (String) f[1]));
  }
}
