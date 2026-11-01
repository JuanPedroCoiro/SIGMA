package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CrearUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.ModificarUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.RestablecerContrasenaRequest;
import com.ciemsa.sigma.administracion.dto.UsuarioResponse;
import com.ciemsa.sigma.shared.domain.ConflictoException;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión de usuarios por la Jefatura (CU-13). */
@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioService {

  private final UsuarioRepository usuarioRepository;
  private final CuadrillaRepository cuadrillaRepository;
  private final PasswordEncoder passwordEncoder;
  private final UsuarioActual usuarioActual;

  /** CU-13a: alta con contraseña inicial. */
  public UsuarioResponse crear(CrearUsuarioRequest req) {
    String correo = Usuario.normalizarCorreo(req.correo());
    if (usuarioRepository.existsByCorreo(correo)) {
      throw correoEnUso(correo); // 4a
    }
    Usuario usuario =
        Usuario.crear(
            req.nombre(),
            correo,
            passwordEncoder.encode(req.password()),
            req.rol(),
            buscarCuadrilla(req.rol(), req.idCuadrilla())); // 4b lo valida la entidad
    guardar(usuario);
    return UsuarioResponse.de(usuario);
  }

  /** CU-13b: datos personales, rol o cuadrilla. El rol nuevo rige desde el siguiente ingreso. */
  public UsuarioResponse modificar(Long id, ModificarUsuarioRequest req) {
    Usuario usuario = buscar(id);
    String correo = Usuario.normalizarCorreo(req.correo());
    if (usuarioRepository.existsByCorreoAndIdNot(correo, id)) {
      throw correoEnUso(correo); // 3a
    }
    if (req.rol() != Rol.JEFATURA) {
      verificarQueQuedeOtraJefatura(usuario);
    }
    usuario.modificar(
        req.nombre(), correo, req.rol(), buscarCuadrilla(req.rol(), req.idCuadrilla()));
    guardar(usuario);
    return UsuarioResponse.de(usuario);
  }

  /** CU-13b: la Jefatura asigna una contraseña nueva; también levanta un bloqueo vigente. */
  public void restablecerContrasena(Long id, RestablecerContrasenaRequest req) {
    buscar(id).restablecerContrasena(passwordEncoder.encode(req.password()));
  }

  /** CU-13c: listado paginado, con filtros opcionales por rol y por estado. */
  @Transactional(readOnly = true)
  public Page<UsuarioResponse> listar(Rol rol, Boolean activo, Pageable pageable) {
    Specification<Usuario> filtro = Specification.unrestricted();
    if (rol != null) {
      filtro = filtro.and((usuario, query, cb) -> cb.equal(usuario.get("rol"), rol));
    }
    if (activo != null) {
      filtro = filtro.and((usuario, query, cb) -> cb.equal(usuario.get("activo"), activo));
    }
    return usuarioRepository.findAll(filtro, pageable).map(UsuarioResponse::de);
  }

  /** CU-13c: detalle. */
  @Transactional(readOnly = true)
  public UsuarioResponse obtener(Long id) {
    return UsuarioResponse.de(buscar(id));
  }

  /** CU-13d: baja lógica; la cuenta no puede ingresar pero conserva su historial. */
  public void desactivar(Long id) {
    if (id.equals(usuarioActual.id())) {
      throw new ReglaNegocioException("No podés desactivar tu propia cuenta.");
    }
    Usuario usuario = buscar(id);
    verificarQueQuedeOtraJefatura(usuario);
    // TODO sprint 2 (3a): advertir si integra una cuadrilla con OTs asignadas o en proceso.
    usuario.darDeBaja();
  }

  /** Vuelve a habilitar una cuenta desactivada. */
  public void activar(Long id) {
    buscar(id).reactivar();
  }

  private Usuario buscar(Long id) {
    return usuarioRepository
        .findById(id)
        .orElseThrow(() -> new NoEncontradoException("No existe el usuario " + id + "."));
  }

  /** Solo los técnicos llevan cuadrilla; para los demás roles se ignora. */
  private Cuadrilla buscarCuadrilla(Rol rol, Long idCuadrilla) {
    if (!rol.requiereCuadrilla() || idCuadrilla == null) {
      return null;
    }
    return cuadrillaRepository
        .findById(idCuadrilla)
        .orElseThrow(() -> new ReglaNegocioException("La cuadrilla elegida no existe."));
  }

  /** Sin ninguna Jefatura activa, nadie podría volver a gestionar usuarios. */
  private void verificarQueQuedeOtraJefatura(Usuario usuario) {
    if (usuario.getRol() == Rol.JEFATURA
        && usuario.isActivo()
        && usuarioRepository.countByRolAndActivoTrue(Rol.JEFATURA) <= 1) {
      throw new ReglaNegocioException("Tiene que quedar al menos una Jefatura activa.");
    }
  }

  /**
   * Guarda y fuerza el SQL acá, para traducir el choque con uq_usuario_correo si dos altas con el
   * mismo correo llegan a la vez y las dos pasan el exists previo.
   */
  private void guardar(Usuario usuario) {
    try {
      usuarioRepository.saveAndFlush(usuario);
    } catch (DataIntegrityViolationException ex) {
      throw correoEnUso(usuario.getCorreo());
    }
  }

  private static ConflictoException correoEnUso(String correo) {
    return new ConflictoException(
        "correo-duplicado", "Ya existe un usuario con el correo " + correo + ".");
  }
}
