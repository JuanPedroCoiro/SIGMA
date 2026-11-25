package com.ciemsa.sigma.ot.domain;

import com.ciemsa.sigma.shared.domain.ConflictoException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Al último cierre le falta la foto Antes o la Después (HU-09, CU-5 3a) (409). No impide validar:
 * el frontend muestra la advertencia y, si la Jefatura confirma, vuelve a pedir con {@code
 * aceptarSinFotos}.
 */
public class FotosFaltantesException extends ConflictoException {

  private final List<MomentoFoto> momentos;

  public FotosFaltantesException(List<MomentoFoto> momentos) {
    super(
        "fotos-faltantes",
        "Al cierre le falta la foto "
            + momentos.stream()
                .map(FotosFaltantesException::nombre)
                .collect(Collectors.joining(" y "))
            + ". Confirmá si querés validarla igual.");
    this.momentos = List.copyOf(momentos);
  }

  /** Los momentos sin foto, para que el frontend no los saque del mensaje. */
  @Override
  public Map<String, Object> getPropiedades() {
    return Map.of("momentosFaltantes", momentos);
  }

  private static String nombre(MomentoFoto momento) {
    return switch (momento) {
      case ANTES -> "del antes";
      case DURANTE -> "del durante";
      case DESPUES -> "del después";
    };
  }
}
