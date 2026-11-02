package com.ciemsa.sigma.activos;

import com.ciemsa.sigma.activos.dto.InstalacionResponse;
import com.ciemsa.sigma.activos.dto.InstalacionResumen;
import com.ciemsa.sigma.activos.dto.NuevaInstalacion;
import java.util.Collection;
import java.util.List;
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

  /** La instalación, si existe y está activa (CU-1 3a). */
  @Transactional(readOnly = true)
  public Optional<InstalacionResumen> buscarActiva(Long id) {
    return instalacionRepository
        .findById(id)
        .filter(Instalacion::isActiva)
        .map(InstalacionResumen::de);
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
