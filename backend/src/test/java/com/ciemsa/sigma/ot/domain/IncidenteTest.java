package com.ciemsa.sigma.ot.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IncidenteTest {

  private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");

  @Test
  void registrar_quedaAbierto() {
    Incidente incidente = incidente(Prioridad.MEDIA);

    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.ABIERTO);
    assertThat(incidente.getIdUsuario()).isEqualTo(3L);
  }

  @Test
  void generarOrdenCorrectiva_conRegla_pasaAConOtYCalculaElVencimiento() {
    Incidente incidente = incidente(Prioridad.MEDIA);

    OrdenTrabajo ot =
        incidente.generarOrdenCorrectiva(AHORA, ReglaSLA.crear(1L, Prioridad.MEDIA, 8, 48));

    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.CON_OT);
    assertThat(ot.getIncidente()).isSameAs(incidente);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getFechaLimite()).isEqualTo(Instant.parse("2026-11-04T13:00:00Z"));
  }

  @Test
  void generarOrdenCorrectiva_sinRegla_otConSlaSinDefinir() {
    OrdenTrabajo ot = incidente(Prioridad.MEDIA).generarOrdenCorrectiva(AHORA, null);

    assertThat(ot.getFechaLimite()).isNull();
    assertThat(ot.getInicioSla()).isEqualTo(AHORA);
  }

  @Test
  void generarOrdenCorrectiva_reglaDeOtraPrioridad_lanzaIllegalArgument() {
    Incidente incidente = incidente(Prioridad.ALTA);
    ReglaSLA reglaBaja = ReglaSLA.crear(1L, Prioridad.BAJA, 24, 120);

    assertThatThrownBy(() -> incidente.generarOrdenCorrectiva(AHORA, reglaBaja))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.ABIERTO);
  }

  @Test
  void generarOrdenCorrectiva_dosVeces_lanzaReglaNegocio() {
    Incidente incidente = incidente(Prioridad.ALTA);
    incidente.generarOrdenCorrectiva(AHORA, null);

    assertThatThrownBy(() -> incidente.generarOrdenCorrectiva(AHORA, null))
        .isInstanceOf(ReglaNegocioException.class);
  }

  @Test
  void ponerEnEsperaDeTercero_quedaEnEsperaYSinOt() {
    Incidente incidente = incidente(Prioridad.ALTA);

    incidente.ponerEnEsperaDeTercero(" UTE ", " Corte de energía ");

    assertThat(incidente.estaEnEsperaDeTercero()).isTrue();
    assertThat(incidente.getTercero()).isEqualTo("UTE");
    assertThat(incidente.getCausa()).isEqualTo("Corte de energía");
    assertThatThrownBy(() -> incidente.generarOrdenCorrectiva(AHORA, null))
        .isInstanceOf(ReglaNegocioException.class);
  }

  @Test
  void ponerEnEsperaDeTercero_sinTercero_lanzaReglaNegocio() {
    Incidente incidente = incidente(Prioridad.ALTA);

    assertThatThrownBy(() -> incidente.ponerEnEsperaDeTercero(" ", null))
        .isInstanceOf(ReglaNegocioException.class);
    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.ABIERTO);
  }

  private static Incidente incidente(Prioridad prioridad) {
    return Incidente.registrar(
        10L, OrigenIncidente.RECLAMO_ORGANISMO, 2L, prioridad, "Semáforo apagado", 3L);
  }
}
