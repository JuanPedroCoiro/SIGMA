package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.administracion.dto.CuadrillaOpcion;
import com.ciemsa.sigma.administracion.dto.CuadrillaResumen;
import com.ciemsa.sigma.administracion.dto.MiCuadrillaResponse;
import com.ciemsa.sigma.administracion.eventos.EventosUsuario.CuadrillaCambiada;
import com.ciemsa.sigma.ot.application.OrdenTrabajoConsultaService;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El técnico se cambia de cuadrilla (HU-20): cuando falta alguien, se suma a otra para el día y
 * vuelve a la suya él mismo. Siempre actúa sobre el usuario actual. Las OTs no se tocan: AccesoOt y
 * MisOtsService leen la cuadrilla de la base, así que desde el pedido siguiente ve las de la nueva.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MiCuadrillaService {

  private final UsuarioRepository usuarioRepository;
  private final CuadrillaRepository cuadrillaRepository;
  private final OrdenTrabajoConsultaService ordenTrabajoConsultaService;
  private final UsuarioActual usuarioActual;
  private final Clock clock;
  private final ApplicationEventPublisher eventos;

  /** La cuadrilla actual, cuántas OTs dejaría de ver al cambiarse y las cuadrillas activas. */
  @Transactional(readOnly = true)
  public MiCuadrillaResponse consultar() {
    return respuesta(usuario());
  }

  /** A la misma cuadrilla no cambia nada ni va a la bitácora. */
  public MiCuadrillaResponse cambiar(Long idCuadrilla) {
    Usuario usuario = usuario();
    Cuadrilla nueva =
        cuadrillaRepository
            .findById(idCuadrilla)
            .orElseThrow(
                () -> new NoEncontradoException("No existe la cuadrilla " + idCuadrilla + "."));
    Long idAnterior = idDe(usuario.getCuadrilla());
    if (usuario.cambiarDeCuadrilla(nueva)) {
      usuarioRepository.saveAndFlush(usuario);
      eventos.publishEvent(
          new CuadrillaCambiada(
              usuario.getId(), idAnterior, nueva.getId(), usuario.getId(), Instant.now(clock)));
    }
    return respuesta(usuario);
  }

  private Usuario usuario() {
    Long id = usuarioActual.id();
    return usuarioRepository
        .findById(id)
        .orElseThrow(() -> new NoEncontradoException("No existe el usuario " + id + "."));
  }

  private MiCuadrillaResponse respuesta(Usuario usuario) {
    Cuadrilla actual = usuario.getCuadrilla();
    List<CuadrillaOpcion> activas =
        cuadrillaRepository.findByActivaTrueOrderByNombreAsc().stream()
            .map(CuadrillaOpcion::de)
            .toList();
    if (actual == null) {
      return new MiCuadrillaResponse(null, 0, 0, activas);
    }
    return new MiCuadrillaResponse(
        CuadrillaResumen.de(actual),
        ordenTrabajoConsultaService.contarAbiertasDeCuadrilla(actual.getId()),
        ordenTrabajoConsultaService.contarEnCursoDeCuadrilla(actual.getId()),
        activas);
  }

  private static Long idDe(Cuadrilla cuadrilla) {
    return cuadrilla == null ? null : cuadrilla.getId();
  }
}
