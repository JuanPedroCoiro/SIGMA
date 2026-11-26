package com.ciemsa.sigma.shared.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la sesión actual. El login (POST) y el logout (DELETE) sobre esta misma URL los
 * resuelve Spring Security en SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/sesion")
@RequiredArgsConstructor
class SesionController {

  private final CuadrillaDelUsuario cuadrillaDelUsuario;

  /**
   * La cuadrilla sale de la base y no de la sesión: puede cambiar con la sesión abierta (HU-20).
   */
  @GetMapping
  SesionResponse actual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
    return SesionResponse.de(
        usuario, cuadrillaDelUsuario.idCuadrillaDe(usuario.getId()).orElse(null));
  }
}
