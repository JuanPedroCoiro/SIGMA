package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas sobre las OTs, para otros módulos y para la asignación. No depende de UsuarioService ni
 * de CuadrillaService, así que administracion lo puede usar sin formar un ciclo con los servicios
 * de ot que sí dependen de ellos.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrdenTrabajoConsultaService {

  /** Las que una cuadrilla tiene entre manos (CU-13d 3a y carga de HU-05). */
  private static final List<EstadoOT> EN_MANOS_DE_LA_CUADRILLA =
      List.of(EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  private final OrdenTrabajoRepository ordenTrabajoRepository;

  /** OTs asignadas, en proceso o en pausa de la cuadrilla. */
  public long contarAbiertasDeCuadrilla(Long idCuadrilla) {
    return ordenTrabajoRepository.countByIdCuadrillaAndEstadoIn(
        idCuadrilla, EN_MANOS_DE_LA_CUADRILLA);
  }

  /**
   * HU-05 (3): cuántas OTs tiene entre manos cada cuadrilla para esa fecha, como referencia al
   * asignar. Las cuadrillas que no figuran no tienen ninguna.
   */
  public List<CargaCuadrilla> cargaPorCuadrilla(LocalDate fecha) {
    return ordenTrabajoRepository
        .contarPorCuadrillaParaFecha(Objects.requireNonNull(fecha), EN_MANOS_DE_LA_CUADRILLA)
        .stream()
        .map(fila -> new CargaCuadrilla(fila.getIdCuadrilla(), fila.getCantidad()))
        .toList();
  }
}
