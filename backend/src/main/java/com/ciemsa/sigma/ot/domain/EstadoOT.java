package com.ciemsa.sigma.ot.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Estados de la OT con patrón State (Fig. 18): cada estado sobreescribe solo las operaciones que
 * admite; el resto lanza {@link TransicionInvalidaException}. Es el único lugar donde se decide una
 * transición. El vencimiento del SLA no es un estado: lo calcula {@link
 * OrdenTrabajo#estaVencida(Instant)}.
 */
public enum EstadoOT {
  PENDIENTE {
    @Override
    void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fechaEjecucion, Instant ahora) {
      ot.registrarAsignacion(
          idCuadrilla, fechaEjecucion, ahora); // asignar o tomar // asignar o tomar
      ot.cambiarEstado(ASIGNADA, ahora);
    }

    @Override
    void pausar(
        OrdenTrabajo ot, MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
      pasarAPausa(ot, motivo, detalle, idUsuario, ahora);
    }

    @Override
    void anular(OrdenTrabajo ot, String motivo, Long idUsuario, Instant ahora) {
      pasarAAnulada(ot, motivo, ahora);
    }
  },
  ASIGNADA {
    @Override
    void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fechaEjecucion, Instant ahora) {
      ot.registrarAsignacion(idCuadrilla, fechaEjecucion, ahora); // asignar o tomar // reasignar
    }

    @Override
    void liberar(OrdenTrabajo ot, Instant ahora) {
      pasarALiberada(ot, ahora);
    }

    @Override
    void iniciar(OrdenTrabajo ot, Instant ahora) {
      ot.cambiarEstado(EN_PROCESO, ahora);
    }

    @Override
    void pausar(
        OrdenTrabajo ot, MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
      pasarAPausa(ot, motivo, detalle, idUsuario, ahora);
    }

    @Override
    void anular(OrdenTrabajo ot, String motivo, Long idUsuario, Instant ahora) {
      pasarAAnulada(ot, motivo, ahora);
    }
  },
  EN_PROCESO {
    @Override
    void liberar(OrdenTrabajo ot, Instant ahora) {
      pasarALiberada(ot, ahora); // la cuadrilla suelta una OT empezada y la sigue otra
    }

    @Override
    void pausar(
        OrdenTrabajo ot, MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
      pasarAPausa(ot, motivo, detalle, idUsuario, ahora);
    }

    @Override
    void cerrarTecnicamente(OrdenTrabajo ot, Instant ahora) {
      ot.cambiarEstado(CERRADA_TECNICA, ahora);
    }
  },
  EN_PAUSA {
    /** Vuelve al estado anterior; si la pausa fue por un tercero, el SLA arranca de nuevo. */
    @Override
    void reanudar(OrdenTrabajo ot, Long idUsuario, Instant ahora) {
      EstadoOT anterior = ot.getEstadoAnteriorPausa();
      ot.cerrarPausa(idUsuario, ahora, true);
      ot.cambiarEstado(anterior, ahora);
    }

    /**
     * Solo si la OT no había empezado: por ejemplo, el tercero resolvió y ya no hace falta. Cierra
     * la pausa abierta con quien anula.
     */
    @Override
    void anular(OrdenTrabajo ot, String motivo, Long idUsuario, Instant ahora) {
      EstadoOT anterior = ot.getEstadoAnteriorPausa();
      if (anterior != PENDIENTE && anterior != ASIGNADA) {
        throw new TransicionInvalidaException(this, "anular");
      }
      ot.registrarAnulacion(
          motivo,
          ahora); // valida el motivo antes de tocar la pausa // valida el motivo antes de tocar la
      // pausa
      ot.cerrarPausa(idUsuario, ahora, false);
      ot.cambiarEstado(ANULADA, ahora);
    }
  },
  CERRADA_TECNICA {
    @Override
    void validar(OrdenTrabajo ot, Instant ahora) {
      ot.cambiarEstado(VALIDADA, ahora);
    }

    @Override
    void observar(OrdenTrabajo ot, Instant ahora) {
      ot.cambiarEstado(ASIGNADA, ahora); // vuelve a la misma cuadrilla
    }

    @Override
    public boolean estaAbierta() {
      return false;
    }
  },
  VALIDADA {
    @Override
    public boolean estaAbierta() {
      return false;
    }
  },
  ANULADA {
    @Override
    public boolean estaAbierta() {
      return false;
    }
  };

  /** Si la OT sigue pendiente de resolución: solo una OT abierta puede vencer. */
  public boolean estaAbierta() {
    return true;
  }

  // Por defecto ninguna operación está permitida: cada estado habilita solo las suyas

  void asignar(OrdenTrabajo ot, Long idCuadrilla, LocalDate fechaEjecucion, Instant ahora) {
    throw new TransicionInvalidaException(this, "asignar");
  }

  void liberar(OrdenTrabajo ot, Instant ahora) {
    throw new TransicionInvalidaException(this, "liberar");
  }

  void iniciar(OrdenTrabajo ot, Instant ahora) {
    throw new TransicionInvalidaException(this, "iniciar");
  }

  void pausar(OrdenTrabajo ot, MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
    throw new TransicionInvalidaException(this, "pausar");
  }

  void reanudar(OrdenTrabajo ot, Long idUsuario, Instant ahora) {
    throw new TransicionInvalidaException(this, "reanudar");
  }

  void cerrarTecnicamente(OrdenTrabajo ot, Instant ahora) {
    throw new TransicionInvalidaException(this, "cerrar");
  }

  void validar(OrdenTrabajo ot, Instant ahora) {
    throw new TransicionInvalidaException(this, "validar");
  }

  void observar(OrdenTrabajo ot, Instant ahora) {
    throw new TransicionInvalidaException(this, "observar");
  }

  void anular(OrdenTrabajo ot, String motivo, Long idUsuario, Instant ahora) {
    throw new TransicionInvalidaException(this, "anular");
  }

  private static void pasarAPausa(
      OrdenTrabajo ot, MotivoPausa motivo, String detalle, Long idUsuario, Instant ahora) {
    ot.abrirPausa(motivo, detalle, idUsuario, ahora);
    ot.cambiarEstado(EN_PAUSA, ahora);
  }

  private static void pasarALiberada(OrdenTrabajo ot, Instant ahora) {
    ot.quitarCuadrilla(ahora);
    ot.cambiarEstado(PENDIENTE, ahora);
  }

  private static void pasarAAnulada(OrdenTrabajo ot, String motivo, Instant ahora) {
    ot.registrarAnulacion(motivo, ahora);
    ot.cambiarEstado(ANULADA, ahora);
  }
}
