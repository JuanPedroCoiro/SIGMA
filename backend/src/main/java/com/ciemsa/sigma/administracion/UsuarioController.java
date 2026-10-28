package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CrearUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.ModificarUsuarioRequest;
import com.ciemsa.sigma.administracion.dto.RestablecerContrasenaRequest;
import com.ciemsa.sigma.administracion.dto.UsuarioResponse;
import com.ciemsa.sigma.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de usuarios (CU-13): solo la Jefatura, verificado en cada pedido. */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JEFATURA')")
public class UsuarioController {

  private final UsuarioService usuarioService;

  /** CU-13a. */
  @PostMapping
  public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest req) {
    UsuarioResponse usuario = usuarioService.crear(req);
    return ResponseEntity.created(URI.create("/api/v1/usuarios/" + usuario.id())).body(usuario);
  }

  /** CU-13b. */
  @PutMapping("/{id}")
  public UsuarioResponse modificar(
      @PathVariable Long id, @Valid @RequestBody ModificarUsuarioRequest req) {
    return usuarioService.modificar(id, req);
  }

  /** CU-13b: restablecer la contraseña. */
  @PostMapping("/{id}/contrasena")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restablecerContrasena(
      @PathVariable Long id, @Valid @RequestBody RestablecerContrasenaRequest req) {
    usuarioService.restablecerContrasena(id, req);
  }

  /** CU-13c: listado, ordenado por nombre si no se indica otro orden. */
  @GetMapping
  public PageResponse<UsuarioResponse> listar(
      @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {
    return PageResponse.de(usuarioService.listar(pageable));
  }

  /** CU-13c: detalle. */
  @GetMapping("/{id}")
  public UsuarioResponse obtener(@PathVariable Long id) {
    return usuarioService.obtener(id);
  }

  /** CU-13d: baja lógica. */
  @PostMapping("/{id}/baja")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void darDeBaja(@PathVariable Long id) {
    usuarioService.darDeBaja(id);
  }
}
