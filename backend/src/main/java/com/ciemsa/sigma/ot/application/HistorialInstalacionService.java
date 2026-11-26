package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import com.ciemsa.sigma.ot.infrastructure.FilaHistorial;
import com.ciemsa.sigma.ot.infrastructure.HistorialInstalacionLectura;
import java.util.function.BiPredicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historial de la instalación (HU-10, CU-8) para todos los roles: las OTs Cerradas técnicamente,
 * Validadas y Anuladas, y los incidentes sin OT, de lo más nuevo a lo más viejo. Las OTs en curso
 * están en la ficha ({@link FichaInstalacionService}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistorialInstalacionService {

  private final InstalacionService instalacionService;
  private final HistorialInstalacionLectura lectura;
  private final AccesoOt accesoOt;

  /** 404 si la instalación no existe; las inactivas también tienen historial. */
  public Page<EntradaHistorial> listar(Long idInstalacion, Pageable pageable) {
    instalacionService.obtenerFicha(idInstalacion);
    BiPredicate<Long, EstadoOT> puedeVer = accesoOt.puedeVerPorCuadrillaYEstado();
    return lectura.pagina(idInstalacion, pageable).map(fila -> entrada(fila, puedeVer));
  }

  private static EntradaHistorial entrada(FilaHistorial f, BiPredicate<Long, EstadoOT> puedeVer) {
    boolean esOt = EntradaHistorial.Clase.OT.name().equals(f.clase());
    EstadoOT estadoOt = esOt ? EstadoOT.valueOf(f.estado()) : null;
    EntradaHistorial.Incidente incidente =
        f.idIncidente() == null
            ? null
            : new EntradaHistorial.Incidente(
                f.idIncidente(),
                f.numeroIncidente(),
                f.categoria(),
                f.prioridad() == null ? null : Prioridad.valueOf(f.prioridad()),
                f.descripcion());
    return new EntradaHistorial(
        esOt ? EntradaHistorial.Clase.OT : EntradaHistorial.Clase.INCIDENTE,
        f.id(),
        f.numero(),
        estadoOt,
        esOt ? null : EstadoIncidente.valueOf(f.estado()),
        f.tipo() == null ? null : TipoOT.valueOf(f.tipo()),
        f.fecha(),
        f.reportadoEn(),
        f.iniciadaEn(),
        f.cerradaEn(),
        f.idCuadrilla() == null ? null : new DetalleOt.Cuadrilla(f.idCuadrilla(), f.cuadrilla()),
        incidente,
        f.diagnostico(),
        f.solucion(),
        f.observaciones(),
        f.motivoAnulacion(),
        f.tercero(),
        f.causa(),
        f.fotosIncidente(),
        f.fotosCierre(),
        esOt && puedeVer.test(f.idCuadrilla(), estadoOt));
  }
}
