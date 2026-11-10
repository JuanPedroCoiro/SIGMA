package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import com.ciemsa.sigma.administracion.dto.CuadrillaResumen;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de cuadrillas por la Jefatura: alta, edición, desactivación y consulta. */
@Service
@RequiredArgsConstructor
@Transactional
public class CuadrillaService {

  private final CuadrillaRepository cuadrillaRepository;
  private final UsuarioRepository usuarioRepository;

  /** Todas las cuadrillas con su estado y sus técnicos activos, por nombre (sin paginar). */
  @Transactional(readOnly = true)
  public List<CuadrillaResponse> listar() {
    return cuadrillaRepository.listarConTecnicosActivos(Rol.TECNICO);
  }

  public CuadrillaResponse crear(CuadrillaRequest req) {
    String nombre = Cuadrilla.normalizarNombre(req.nombre());
    if (cuadrillaRepository.existsByNombreIgnoreCase(nombre)) {
      throw nombreEnUso(nombre);
    }
    Cuadrilla cuadrilla = Cuadrilla.crear(nombre, req.zona());
    guardar(cuadrilla);
    return CuadrillaResponse.de(cuadrilla, 0);
  }

  /** La cuadrilla, si existe y está activa: para asignarle OTs (HU-05). */
  @Transactional(readOnly = true)
  public Optional<CuadrillaResumen> buscarActiva(Long id) {
    return cuadrillaRepository.findById(id).filter(Cuadrilla::isActiva).map(CuadrillaResumen::de);
  }

  public CuadrillaResponse modificar(Long id, CuadrillaRequest req) {
    Cuadrilla cuadrilla = buscar(id);
    String nombre = Cuadrilla.normalizarNombre(req.nombre());
    if (cuadrillaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
      throw nombreEnUso(nombre);
    }
    cuadrilla.modificar(nombre, req.zona());
    guardar(cuadrilla);
    return CuadrillaResponse.de(cuadrilla, contarTecnicosActivos(id));
  }

  /** Baja lógica; la entidad la rechaza si quedan técnicos activos en la cuadrilla. */
  public void desactivar(Long id) {
    buscar(id).desactivar(contarTecnicosActivos(id));
  }

  public void activar(Long id) {
    buscar(id).activar();
  }

  private long contarTecnicosActivos(Long idCuadrilla) {
    return usuarioRepository.countByCuadrillaIdAndRolAndActivoTrue(idCuadrilla, Rol.TECNICO);
  }

  private Cuadrilla buscar(Long id) {
    return cuadrillaRepository
        .findById(id)
        .orElseThrow(() -> new NoEncontradoException("No existe la cuadrilla " + id + "."));
  }

  /** Fuerza el SQL acá para traducir el choque con uq_cuadrilla_nombre si dos altas compiten. */
  private void guardar(Cuadrilla cuadrilla) {
    try {
      cuadrillaRepository.saveAndFlush(cuadrilla);
    } catch (DataIntegrityViolationException ex) {
      throw nombreEnUso(cuadrilla.getNombre());
    }
  }

  private static ConflictoException nombreEnUso(String nombre) {
    return new ConflictoException(
        "cuadrilla-duplicada", "Ya existe una cuadrilla con el nombre " + nombre + ".");
  }
}
