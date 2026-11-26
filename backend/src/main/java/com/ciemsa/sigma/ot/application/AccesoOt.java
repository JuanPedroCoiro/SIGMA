package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.Rol;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Qué OTs puede ver y operar el usuario actual. Operador y Jefatura, cualquiera. Un técnico opera
 * solo las asignadas a su cuadrilla actual (leída de la base, no de la sesión). Para ver, además,
 * cualquier OT terminada (Cerrada técnicamente, Validada o Anulada), aunque sea de otra cuadrilla:
 * revisa el historial del cruce antes de intervenir (HU-10). Las en curso de otra cuadrilla no las
 * ve. Las fotos del incidente siguen a su OT; las de un incidente sin OT, las ve si está en el
 * historial (en espera de un tercero o cerrado sin OT). Es el único lugar con estas reglas: las
 * usan el detalle, las pausas, el inicio, el cierre, las fotos, y la ficha y el historial de la
 * instalación.
 */
@Component
@RequiredArgsConstructor
class AccesoOt {

  private final OrdenTrabajoRepository ordenTrabajoRepository;
  private final IncidenteRepository incidenteRepository;
  private final UsuarioService usuarioService;
  private final UsuarioActual usuarioActual;

  /**
   * Para ver (detalle, fotos del cierre). 404 si no existe; 403 si es un técnico y la OT sigue en
   * curso sin estar asignada a su cuadrilla actual.
   */
  OrdenTrabajo buscar(Long idOrdenTrabajo) {
    OrdenTrabajo ot = existente(ordenTrabajoRepository.findById(idOrdenTrabajo), idOrdenTrabajo);
    if (esTecnico() && ot.getEstado().estaAbierta() && !esDeSuCuadrilla(ot)) {
      throw new AccessDeniedException("La OT no está asignada a tu cuadrilla.");
    }
    return ot;
  }

  /**
   * Para operar sin bloquear la fila (pausar, reanudar). 404 si no existe; 403 si es un técnico y
   * la OT no está asignada a su cuadrilla actual, aunque esté terminada.
   */
  OrdenTrabajo buscarParaOperar(Long idOrdenTrabajo) {
    return deSuCuadrilla(
        existente(ordenTrabajoRepository.findById(idOrdenTrabajo), idOrdenTrabajo));
  }

  /**
   * Como {@link #buscarParaOperar}, con la fila bloqueada hasta el fin de la transacción: dos
   * envíos a la vez sobre la misma OT se atienden de a uno.
   */
  OrdenTrabajo buscarParaModificar(Long idOrdenTrabajo) {
    return deSuCuadrilla(
        existente(ordenTrabajoRepository.findConBloqueoById(idOrdenTrabajo), idOrdenTrabajo));
  }

  /**
   * Las fotos del incidente (#85, HU-10). Operador y Jefatura, todas. Un técnico, las del incidente
   * cuya OT puede ver ({@link #buscar}: terminada, o en curso de su cuadrilla); si el incidente no
   * tiene OT, las de los que entran al historial de la instalación (en espera de un tercero o
   * cerrado sin OT), no las de uno abierto. Quien llama ya verificó que el incidente existe.
   */
  void verificarIncidente(Long idIncidente) {
    if (!esTecnico()) {
      return;
    }
    boolean puede =
        ordenTrabajoRepository
            .findByIncidenteId(idIncidente)
            .map(ot -> puedeVer().test(ot))
            .orElseGet(() -> sinOtEnHistorial(idIncidente));
    if (!puede) {
      throw new AccessDeniedException("No podés ver las fotos de este incidente.");
    }
  }

  /**
   * La regla de {@link #buscar}, para marcar en una lista qué OTs puede abrir el usuario (ficha de
   * la instalación, HU-10). La cuadrilla del técnico se lee una sola vez.
   */
  Predicate<OrdenTrabajo> puedeVer() {
    BiPredicate<Long, EstadoOT> puedeVer = puedeVerPorCuadrillaYEstado();
    return ot -> puedeVer.test(ot.getIdCuadrilla(), ot.getEstado());
  }

  /**
   * Como {@link #puedeVer()}, por el id de la cuadrilla de la OT (null si no tiene) y su estado,
   * para las consultas que traen filas y no entidades (historial de la instalación, HU-10).
   */
  BiPredicate<Long, EstadoOT> puedeVerPorCuadrillaYEstado() {
    if (!esTecnico()) {
      return (idCuadrilla, estado) -> true;
    }
    Optional<Long> suCuadrilla = usuarioService.idCuadrillaDe(usuarioActual.id());
    return (idCuadrilla, estado) ->
        !estado.estaAbierta() || esDeLaCuadrilla(idCuadrilla, suCuadrilla);
  }

  private static OrdenTrabajo existente(Optional<OrdenTrabajo> encontrada, Long idOrdenTrabajo) {
    return encontrada.orElseThrow(
        () -> new NoEncontradoException("No existe la orden de trabajo " + idOrdenTrabajo + "."));
  }

  private boolean sinOtEnHistorial(Long idIncidente) {
    return incidenteRepository
        .findById(idIncidente)
        .map(Incidente::getEstado)
        .map(EstadoIncidente.SIN_OT_EN_HISTORIAL::contains)
        .orElse(false);
  }

  private OrdenTrabajo deSuCuadrilla(OrdenTrabajo ot) {
    if (esTecnico() && !esDeSuCuadrilla(ot)) {
      throw new AccessDeniedException("La OT no está asignada a tu cuadrilla.");
    }
    return ot;
  }

  private boolean esTecnico() {
    return usuarioActual.rol() == Rol.TECNICO;
  }

  private boolean esDeSuCuadrilla(OrdenTrabajo ot) {
    return ot.getIdCuadrilla() != null
        && esDeLaCuadrilla(ot.getIdCuadrilla(), usuarioService.idCuadrillaDe(usuarioActual.id()));
  }

  private static boolean esDeLaCuadrilla(Long idCuadrillaOt, Optional<Long> suCuadrilla) {
    return idCuadrillaOt != null && suCuadrilla.map(idCuadrillaOt::equals).orElse(false);
  }
}
