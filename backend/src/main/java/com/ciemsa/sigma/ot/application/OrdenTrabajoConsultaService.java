package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas sobre las OTs para otros módulos. No depende de UsuarioService, así que administracion
 * lo puede usar sin formar un ciclo con los servicios de ot que sí dependen de él.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdenTrabajoConsultaService {

  /** Las que una cuadrilla tiene entre manos (CU-13d 3a). */
  private static final List<EstadoOT> EN_MANOS_DE_LA_CUADRILLA =
      List.of(EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  private final OrdenTrabajoRepository ordenTrabajoRepository;

  /** OTs asignadas, en proceso o en pausa de la cuadrilla. */
  public long contarAbiertasDeCuadrilla(Long idCuadrilla) {
    return ordenTrabajoRepository.countByIdCuadrillaAndEstadoIn(
        idCuadrilla, EN_MANOS_DE_LA_CUADRILLA);
  }
}
