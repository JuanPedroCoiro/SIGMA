package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.seguridad.Rol;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** El correo que reciben estas consultas tiene que venir normalizado (Usuario.normalizarCorreo). */
public interface UsuarioRepository
    extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

  Optional<Usuario> findByCorreo(String correo);

  boolean existsByCorreo(String correo);

  boolean existsByCorreoAndIdNot(String correo, Long id);

  long countByRolAndActivoTrue(Rol rol);

  /** Listado de CU-13c con filtros: trae la cuadrilla en la misma consulta para evitar N+1. */
  @Override
  @EntityGraph(attributePaths = "cuadrilla")
  Page<Usuario> findAll(Specification<Usuario> filtro, Pageable pageable);
}
