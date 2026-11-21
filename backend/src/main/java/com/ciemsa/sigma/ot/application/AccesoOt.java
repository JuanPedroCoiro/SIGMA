package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Qué OTs puede ver y operar el usuario actual: un técnico, solo las asignadas a su cuadrilla
 * actual (leída de la base, no de la sesión); Operador y Jefatura, cualquiera. Es el único lugar
 * con esta regla: la usan el detalle, las pausas, el inicio y el cierre.
 */
@Component
@RequiredArgsConstructor
class AccesoOt {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final UsuarioService usuarioService;
  private final UsuarioActual usuarioActual;

  /** 404 si no existe; 403 si es un técnico y la OT no está asignada a su cuadrilla actual. */
  OrdenTrabajo buscar(Long idOrdenTrabajo) {
    return verificar(ordenTrabajoRepository.findById(idOrdenTrabajo), idOrdenTrabajo);
  }

  /**
   * Como {@link #buscar}, con la fila bloqueada hasta el fin de la transacción: dos envíos a la vez
   * sobre la misma OT se atienden de a uno.
   */
  OrdenTrabajo buscarParaModificar(Long idOrdenTrabajo) {
    return verificar(ordenTrabajoRepository.findConBloqueoById(idOrdenTrabajo), idOrdenTrabajo);
  }

  private OrdenTrabajo verificar(Optional<OrdenTrabajo> encontrada, Long idOrdenTrabajo) {
    OrdenTrabajo ot =
        encontrada.orElseThrow(
            () ->
                new NoEncontradoException("No existe la orden de trabajo " + idOrdenTrabajo + "."));
    if (usuarioActual.rol() == Rol.TECNICO && !esDeSuCuadrilla(ot)) {
      throw new AccessDeniedException("La OT no está asignada a tu cuadrilla.");
    }
    return ot;
  }

  private boolean esDeSuCuadrilla(OrdenTrabajo ot) {
    return ot.getIdCuadrilla() != null
        && usuarioService
            .idCuadrillaDe(usuarioActual.id())
            .map(ot.getIdCuadrilla()::equals)
            .orElse(false);
  }
}
