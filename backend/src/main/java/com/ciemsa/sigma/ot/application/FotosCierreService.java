package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.Adjunto;
import com.ciemsa.sigma.ot.domain.FotoInvalidaException;
import com.ciemsa.sigma.ot.domain.TipoImagen;
import com.ciemsa.sigma.ot.infrastructure.AdjuntoRepository;
import com.ciemsa.sigma.shared.archivos.AlmacenArchivos;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Fotos del cierre técnico (#90, HU-08, CU-3 pasos 4, 7 y 9): al menos una y hasta 6 por cierre,
 * cada una con su momento. Se guardan en la transacción del cierre; si vuelve atrás, se borran los
 * archivos ya guardados. Las ve quien puede ver la OT ({@link AccesoOt}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FotosCierreService {

  public static final int MAXIMO_POR_CIERRE = 6;

  private final AdjuntoRepository adjuntoRepository;
  private final AlmacenArchivos almacenArchivos;
  private final AccesoOt accesoOt;
  private final UsuarioActual usuarioActual;

  /** Paso 7: al menos una foto y hasta 6, cada una válida. Devuelve el tipo de cada una. */
  public List<TipoImagen> validar(List<FotoCierre> fotos) {
    if (fotos.isEmpty()) {
      throw new FotoInvalidaException("El cierre técnico exige al menos una foto.");
    }
    if (fotos.size() > MAXIMO_POR_CIERRE) {
      throw new FotoInvalidaException(
          "El cierre técnico admite hasta " + MAXIMO_POR_CIERRE + " fotos.");
    }
    return fotos.stream().map(foto -> foto.foto().validar()).toList();
  }

  /** Paso 9: guarda los archivos y sus filas, dentro de la transacción del cierre. */
  public void guardar(Long idOrdenTrabajo, List<FotoCierre> fotos, List<TipoImagen> tipos) {
    List<String> guardadas = new ArrayList<>();
    borrarSiVuelveAtras(guardadas);
    List<Adjunto> adjuntos = new ArrayList<>();
    for (int i = 0; i < fotos.size(); i++) {
      FotoNueva foto = fotos.get(i).foto();
      TipoImagen tipo = tipos.get(i);
      String ubicacion = almacenArchivos.guardar(foto.contenido(), tipo.extension());
      guardadas.add(ubicacion);
      adjuntos.add(
          Adjunto.fotoDeCierre(
              idOrdenTrabajo,
              fotos.get(i).momento(),
              foto.nombreOriginal(),
              tipo,
              foto.contenido().length,
              ubicacion,
              usuarioActual.id()));
    }
    adjuntoRepository.saveAll(adjuntos);
  }

  /** 404 si la OT no existe; 403 si es un técnico y no es de su cuadrilla. */
  @Transactional(readOnly = true)
  public List<Adjunto> listar(Long idOrdenTrabajo) {
    accesoOt.buscar(idOrdenTrabajo);
    return adjuntoRepository.findByIdOrdenTrabajoOrderByCreadoEnAscIdAsc(idOrdenTrabajo);
  }

  /** Como {@link #listar}; 404 si la foto no existe o es de otra OT. */
  @Transactional(readOnly = true)
  public ArchivoFoto leer(Long idOrdenTrabajo, Long idFoto) {
    accesoOt.buscar(idOrdenTrabajo);
    Adjunto adjunto =
        adjuntoRepository
            .findByIdAndIdOrdenTrabajo(idFoto, idOrdenTrabajo)
            .orElseThrow(() -> new NoEncontradoException("No existe la foto " + idFoto + "."));
    return new ArchivoFoto(adjunto, almacenArchivos.leer(adjunto.getUbicacion()));
  }

  /**
   * Si la transacción no se confirma (falla algo después de guardar los archivos, o el commit), los
   * borra: no quedan archivos sin su fila. Se registra antes de guardar el primero.
   */
  private void borrarSiVuelveAtras(List<String> ubicaciones) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              ubicaciones.forEach(almacenArchivos::borrar);
            }
          }
        });
  }
}
