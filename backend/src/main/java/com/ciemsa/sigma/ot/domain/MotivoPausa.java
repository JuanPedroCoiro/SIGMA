package com.ciemsa.sigma.ot.domain;

/**
 * Motivo por el que se pausa una OT (precisiones del dominio del 01/11, reemplazan la «suspensión»
 * de CU-3 3a). Solo una falla de un tercero reinicia el SLA; los demás se registran y el plazo
 * sigue corriendo. El CHECK ck_pausa_ot_motivo admite los mismos valores.
 */
public enum MotivoPausa {
  /** La causa es de un tercero (UTE, Antel...): el SLA arranca completo al reanudar. */
  TERCERO {
    @Override
    public boolean reiniciaSla() {
      return true;
    }

    @Override
    public boolean requiereDetalle() {
      return true; // qué tercero
    }
  },
  MATERIALES,
  CLIMA,
  FIN_DE_JORNADA,
  OTRO {
    @Override
    public boolean requiereDetalle() {
      return true;
    }
  };

  /** Si lo corrido del SLA deja de contar y el plazo vuelve a empezar al reanudar. */
  public boolean reiniciaSla() {
    return false;
  }

  /** Si la pausa necesita un detalle (por ejemplo, qué tercero). */
  public boolean requiereDetalle() {
    return false;
  }
}
