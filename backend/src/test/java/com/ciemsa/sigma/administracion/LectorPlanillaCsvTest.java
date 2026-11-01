package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.administracion.infrastructure.LectorPlanillaCsv;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LectorPlanillaCsvTest {

  private final LectorPlanillaCsv lector = new LectorPlanillaCsv();

  private Planilla leer(String contenido) {
    return lector.leer(new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void leer_conBomYEncabezadosConMayusculas_losNormaliza() {
    Planilla planilla = leer("﻿ Codigo ;NOMBRE\r\nA-1;Cruce\r\n");

    assertThat(planilla.encabezados()).containsExactly("codigo", "nombre");
    assertThat(planilla.filas()).hasSize(1);
    assertThat(planilla.filas().getFirst().valor("codigo")).isEqualTo("A-1");
  }

  @Test
  void leer_campoEntreComillas_admiteSeparadorYComillasEscapadas() {
    Planilla planilla = leer("codigo;nombre\nA-1;\"Cruce; \"\"norte\"\"\"\n");

    assertThat(planilla.filas().getFirst().valor("nombre")).isEqualTo("Cruce; \"norte\"");
  }

  @Test
  void leer_filasVacias_lasSalteaYConservaElNumeroDeLinea() {
    Planilla planilla = leer("codigo\nA-1\n\n  \nA-2\n");

    assertThat(planilla.filas()).extracting(Planilla.Fila::numero).containsExactly(2, 5);
  }

  @Test
  void leer_filaConMenosCampos_devuelveVacioEnLosFaltantes() {
    Planilla planilla = leer("codigo;nombre\nA-1\n");

    assertThat(planilla.filas().getFirst().valor("nombre")).isEmpty();
  }

  @Test
  void leer_archivoVacio_lanzaPlanillaInvalida() {
    assertThatThrownBy(() -> leer("")).isInstanceOf(PlanillaInvalidaException.class);
  }
}
