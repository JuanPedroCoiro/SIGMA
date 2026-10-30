package com.ciemsa.sigma.administracion.infrastructure;

import com.ciemsa.sigma.administracion.LectorPlanilla;
import com.ciemsa.sigma.administracion.Planilla;
import com.ciemsa.sigma.administracion.PlanillaInvalidaException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * CSV en UTF-8 separado por punto y coma, como lo guarda Excel en español («CSV UTF-8»). Admite el
 * BOM inicial y campos entre comillas dobles (con "" como escape); no admite saltos de línea dentro
 * de un campo.
 */
@Component
public class LectorPlanillaCsv implements LectorPlanilla {

  private static final char SEPARADOR = ';';
  private static final char COMILLA = '"';
  private static final String BOM = "﻿";

  @Override
  public Planilla leer(InputStream archivo) {
    String texto;
    try {
      texto = new String(archivo.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (texto.startsWith(BOM)) texto = texto.substring(1);

    String[] lineas = texto.split("\r?\n", -1);
    if (lineas[0].isBlank()) {
      throw new PlanillaInvalidaException(
          "El archivo está vacío: la primera fila tiene que tener los encabezados.");
    }

    List<String> encabezados =
        separar(lineas[0]).stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).toList();
    List<Planilla.Fila> filas = new ArrayList<>();
    for (int i = 1; i < lineas.length; i++) {
      if (lineas[i].isBlank()) continue;
      List<String> campos = separar(lineas[i]);
      Map<String, String> valores = new HashMap<>();
      for (int c = 0; c < encabezados.size() && c < campos.size(); c++) {
        valores.putIfAbsent(encabezados.get(c), campos.get(c).trim());
      }
      filas.add(new Planilla.Fila(i + 1, valores));
    }
    return new Planilla(encabezados, filas);
  }

  private static List<String> separar(String linea) {
    List<String> campos = new ArrayList<>();
    StringBuilder actual = new StringBuilder();
    boolean entreComillas = false;
    for (int i = 0; i < linea.length(); i++) {
      char c = linea.charAt(i);
      if (entreComillas) {
        if (c == COMILLA && i + 1 < linea.length() && linea.charAt(i + 1) == COMILLA) {
          actual.append(COMILLA);
          i++;
        } else if (c == COMILLA) {
          entreComillas = false;
        } else {
          actual.append(c);
        }
      } else if (c == COMILLA) {
        entreComillas = true;
      } else if (c == SEPARADOR) {
        campos.add(actual.toString());
        actual.setLength(0);
      } else {
        actual.append(c);
      }
    }
    campos.add(actual.toString());
    return campos;
  }
}
