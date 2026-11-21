package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.TipoOT;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una OT de la bandeja «Mis OTs» del técnico (CU-3, HU-07). {@code prioridad}, {@code categoria} y
 * {@code numeroIncidente} son null en las preventivas; {@code iniciadaEn}, si todavía no empezó;
 * {@code pausa}, si no está en pausa. {@code estadoAnteriorPausa} solo en EN_PAUSA. La dirección,
 * la ruta y las coordenadas de la instalación son para abrir el mapa desde el celular.
 */
public record OrdenDeMiCuadrilla(
    Long id,
    String numero,
    TipoOT tipo,
    EstadoOT estado,
    EstadoOT estadoAnteriorPausa,
    Prioridad prioridad,
    String categoria,
    String numeroIncidente,
    LocalDate fechaEjecucion,
    Instant fechaLimite,
    boolean slaDefinido,
    boolean vencida,
    Instant iniciadaEn,
    Instalacion instalacion,
    Pausa pausa) {

  static OrdenDeMiCuadrilla de(FilaOrdenDeMiCuadrilla fila, Instant ahora) {
    return new OrdenDeMiCuadrilla(
        fila.id(),
        fila.numero(),
        fila.tipo(),
        fila.estado(),
        fila.estadoAnteriorPausa(),
        fila.prioridad(),
        fila.categoria(),
        fila.numeroIncidente(),
        fila.fechaEjecucion(),
        fila.fechaLimite(),
        fila.fechaLimite() != null,
        VencimientoSinEntidad.vencida(fila.motivoPausa(), fila.fechaLimite(), ahora),
        fila.iniciadaEn(),
        new Instalacion(
            fila.idInstalacion(),
            fila.codigoInstalacion(),
            fila.nombreInstalacion(),
            fila.direccion(),
            fila.rutaZona(),
            fila.latitud(),
            fila.longitud()),
        fila.motivoPausa() == null
            ? null
            : new Pausa(fila.motivoPausa(), fila.detallePausa(), fila.inicioPausa()));
  }

  public record Instalacion(
      Long id,
      String codigo,
      String nombre,
      String direccion,
      String rutaZona,
      BigDecimal latitud,
      BigDecimal longitud) {}

  public record Pausa(MotivoPausa motivo, String detalle, Instant inicio) {}
}
