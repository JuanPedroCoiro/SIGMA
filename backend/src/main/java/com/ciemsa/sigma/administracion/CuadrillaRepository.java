package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import com.ciemsa.sigma.shared.seguridad.Rol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, Long> {

  boolean existsByNombreIgnoreCase(String nombre);

  boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

  /** Todas las cuadrillas por nombre, con sus técnicos activos contados en la misma consulta. */
  @Query(
      """
      select new com.ciemsa.sigma.administracion.dto.CuadrillaResponse(
          c.id, c.nombre, c.zona, c.activa, count(u))
      from Cuadrilla c
      left join Usuario u on u.cuadrilla = c and u.activo = true and u.rol = :rol
      group by c.id, c.nombre, c.zona, c.activa
      order by c.nombre
      """)
  List<CuadrillaResponse> listarConTecnicosActivos(Rol rol);
}
