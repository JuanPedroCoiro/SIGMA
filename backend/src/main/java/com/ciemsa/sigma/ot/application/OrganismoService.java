package com.ciemsa.sigma.ot.application;

import com.ciemsa.sigma.ot.domain.Organismo;
import com.ciemsa.sigma.ot.infrastructure.OrganismoRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganismoService {

  private final OrganismoRepository organismoRepository;

  public Long obtenerIdPorNombre(String nombre) {
    return organismoRepository
        .findByNombre(nombre)
        .map(Organismo::getId)
        .orElseThrow(() -> new NoEncontradoException("No existe el organismo " + nombre + "."));
  }
}
