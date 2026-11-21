package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.Adjunto;
import com.ciemsa.sigma.ot.domain.DemasiadasFotosException;
import com.ciemsa.sigma.ot.domain.FotoInvalidaException;
import com.ciemsa.sigma.ot.domain.TipoImagen;
import com.ciemsa.sigma.ot.infrastructure.AdjuntoRepository;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.shared.archivos.AlmacenArchivos;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fotos de un incidente (#71): opcionales, hasta 3, JPG/PNG/WEBP de hasta 10 MB cada una. El tipo
 * se reconoce por el contenido. Los archivos van a AlmacenArchivos; en la base, sus datos.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FotosIncidenteService {

  public static final int MAXIMO_POR_INCIDENTE = 3;
  public static final long TAMANIO_MAXIMO = 10L * 1024 * 1024;

  private final IncidenteRepository incidenteRepository;
  private final AdjuntoRepository adjuntoRepository;
  private final AlmacenArchivos almacenArchivos;
  private final UsuarioActual usuarioActual;

  /**
   * Valida todas antes de guardar ninguna: o se suben todas o ninguna. Si algo falla después de
   * guardar archivos, se borran, para no dejar archivos sin su fila en la base.
   */
  public List<Adjunto> subir(Long idIncidente, List<FotoNueva> fotos) {
    exigirIncidente(idIncidente);
    if (fotos.isEmpty()) {
      throw new FotoInvalidaException("Elegí al menos una foto.");
    }
    long yaTiene = adjuntoRepository.countByIdIncidente(idIncidente);
    if (yaTiene + fotos.size() > MAXIMO_POR_INCIDENTE) {
      throw new DemasiadasFotosException(MAXIMO_POR_INCIDENTE, yaTiene);
    }
    List<TipoImagen> tipos = fotos.stream().map(FotosIncidenteService::validar).toList();

    List<String> guardadas = new ArrayList<>();
    try {
      List<Adjunto> adjuntos = new ArrayList<>();
      for (int i = 0; i < fotos.size(); i++) {
        FotoNueva foto = fotos.get(i);
        TipoImagen tipo = tipos.get(i);
        String ubicacion = almacenArchivos.guardar(foto.contenido(), tipo.extension());
        guardadas.add(ubicacion);
        adjuntos.add(
            Adjunto.fotoDeIncidente(
                idIncidente,
                foto.nombreOriginal(),
                tipo,
                foto.contenido().length,
                ubicacion,
                usuarioActual.id()));
      }
      return adjuntoRepository.saveAllAndFlush(adjuntos);
    } catch (RuntimeException e) {
      guardadas.forEach(almacenArchivos::borrar);
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public List<Adjunto> listar(Long idIncidente) {
    exigirIncidente(idIncidente);
    return adjuntoRepository.findByIdIncidenteOrderByCreadoEnAscIdAsc(idIncidente);
  }

  /** 404 si la foto no existe o es de otro incidente. */
  @Transactional(readOnly = true)
  public ArchivoFoto leer(Long idIncidente, Long idFoto) {
    Adjunto adjunto =
        adjuntoRepository
            .findByIdAndIdIncidente(idFoto, idIncidente)
            .orElseThrow(() -> new NoEncontradoException("No existe la foto " + idFoto + "."));
    return new ArchivoFoto(adjunto, almacenArchivos.leer(adjunto.getUbicacion()));
  }

  private static TipoImagen validar(FotoNueva foto) {
    String nombre = foto.nombreOriginal() == null ? "La foto" : "«" + foto.nombreOriginal() + "»";
    if (foto.contenido().length == 0) {
      throw new FotoInvalidaException(nombre + " está vacía.");
    }
    if (foto.contenido().length > TAMANIO_MAXIMO) {
      throw new FotoInvalidaException(nombre + " supera los 10 MB.");
    }
    return TipoImagen.detectar(foto.contenido())
        .orElseThrow(
            () -> new FotoInvalidaException(nombre + " no es una imagen JPG, PNG ni WEBP."));
  }

  private void exigirIncidente(Long idIncidente) {
    if (!incidenteRepository.existsById(idIncidente)) {
      throw new NoEncontradoException("No existe el incidente " + idIncidente + ".");
    }
  }
}
