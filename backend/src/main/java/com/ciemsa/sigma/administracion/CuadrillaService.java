package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaRequest;
import com.ciemsa.sigma.administracion.dto.CuadrillaResponse;
import com.ciemsa.sigma.administracion.dto.CuadrillaResumen;
import com.ciemsa.sigma.administracion.eventos.DatosCuadrilla;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaActivada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaCreada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaDesactivada;
import com.ciemsa.sigma.administracion.eventos.EventosCuadrilla.CuadrillaModificada;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;

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
    eventos.publishEvent(
        new CuadrillaCreada(
            cuadrilla.getId(), DatosCuadrilla.de(cuadrilla), usuarioActual.id(), ahora()));
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
    DatosCuadrilla anterior = DatosCuadrilla.de(cuadrilla);
    cuadrilla.modificar(nombre, req.zona());
    guardar(cuadrilla);
    eventos.publishEvent(
        new CuadrillaModificada(
            id, anterior, DatosCuadrilla.de(cuadrilla), usuarioActual.id(), ahora()));
    return CuadrillaResponse.de(cuadrilla, contarTecnicosActivos(id));
  }

  /** Baja lógica; la entidad la rechaza si quedan técnicos activos en la cuadrilla. */
  public void desactivar(Long id) {
    Cuadrilla cuadrilla = buscar(id);
    boolean estabaActiva = cuadrilla.isActiva();
    cuadrilla.desactivar(contarTecnicosActivos(id));
    if (estabaActiva) { // si ya estaba inactiva no cambió nada: no va a la bitácora
      eventos.publishEvent(new CuadrillaDesactivada(id, usuarioActual.id(), ahora()));
    }
  }

  public void activar(Long id) {
    Cuadrilla cuadrilla = buscar(id);
    boolean estabaActiva = cuadrilla.isActiva();
    cuadrilla.activar();
    if (!estabaActiva) { // si ya estaba activa no cambió nada: no va a la bitácora
      eventos.publishEvent(new CuadrillaActivada(id, usuarioActual.id(), ahora()));
    }
  }

  private Instant ahora() {
    return Instant.now(clock);
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
