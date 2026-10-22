package com.ciemsa.sigma.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Respuesta de los listados paginados (?page&size&sort). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

  public static <T> PageResponse<T> de(Page<T> pagina) {
    return new PageResponse<>(
        pagina.getContent(), pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
  }
}
