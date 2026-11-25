package com.ciemsa.sigma.activos;

import com.ciemsa.sigma.activos.dto.InstalacionFicha;
import com.ciemsa.sigma.activos.dto.InstalacionResponse;
import com.ciemsa.sigma.activos.dto.InstalacionResumen;
import com.ciemsa.sigma.activos.dto.NuevaInstalacion;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InstalacionService {

  private final InstalacionRepository instalacionRepository;

  /** Listado paginado (HU-02). */
  @Transactional(readOnly = true)
  public Page<InstalacionResponse> listar(Pageable pageable) {
    return instalacionRepository.findAll(pageable).map(InstalacionResponse::de);
  }

  /**
   * Las activas con sus coordenadas, para el mapa del registro de incidentes (HU-11, #70). Sin
   * paginar: la red tiene a lo sumo unos cientos de cruces. Si crece, la respuesta es agrupar los
   * marcadores en el mapa, no paginar.
   */
  @Transactional(readOnly = true)
  public List<InstalacionResponse> listarActivasParaMapa() {
    return instalacionRepository.findByActivaTrueOrderByCodigoAsc().stream()
        .map(InstalacionResponse::de)
        .toList();
  }

  /**
   * Búsqueda por código o nombre para elegir la instalación de un incidente (HU-03): solo las
   * activas, sin distinguir mayúsculas ni tildes.
   */
  @Transactional(readOnly = true)
  public Page<InstalacionResponse> buscarActivas(String texto, Pageable pageable) {
    return instalacionRepository
        .buscarActivas("%" + patronSinTildes(texto) + "%", pageable)
        .map(InstalacionResponse::de);
  }

  /**
   * En minúsculas y sin tildes, con los comodines de LIKE escapados con {@code !} para buscarlos
   * como texto.
   */
  static String patronSinTildes(String texto) {
    String sinTildes =
        Normalizer.normalize(texto.strip(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    return sinTildes.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  /** La instalación, si existe y está activa (CU-1 3a). */
  @Transactional(readOnly = true)
  public Optional<InstalacionResumen> buscarActiva(Long id) {
    return instalacionRepository
        .findById(id)
        .filter(Instalacion::isActiva)
        .map(InstalacionResumen::de);
  }

  /**
   * Datos de la ficha (HU-10, CU-7). También las inactivas: su historial se sigue consultando.
   *
   * @throws NoEncontradoException si no existe
   */
  @Transactional(readOnly = true)
  public InstalacionFicha obtenerFicha(Long id) {
    return instalacionRepository
        .findById(id)
        .map(InstalacionFicha::de)
        .orElseThrow(() -> new NoEncontradoException("No existe la instalación " + id + "."));
  }

  /** De los códigos recibidos, los que ya tienen una instalación. */
  @Transactional(readOnly = true)
  public Set<String> codigosExistentes(Collection<String> codigos) {
    if (codigos.isEmpty()) return Set.of();
    return instalacionRepository.findCodigosExistentes(codigos);
  }

  public int crearTodas(List<NuevaInstalacion> nuevas, Long idOrganismo) {
    List<Instalacion> instalaciones =
        nuevas.stream()
            .map(
                n ->
                    Instalacion.crear(
                        n.codigo(),
                        n.nombre(),
                        n.rutaZona(),
                        n.latitud(),
                        n.longitud(),
                        idOrganismo))
            .toList();
    instalacionRepository.saveAll(instalaciones);
    return instalaciones.size();
  }
}
