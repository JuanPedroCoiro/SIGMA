package com.ciemsa.sigma.shared.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Respuesta de los listados paginados (?page&size&sort); pagina empieza en 0. */
public record PageResponse<T>(
    List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

  public static <T> PageResponse<T> de(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
