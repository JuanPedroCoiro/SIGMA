package com.ciemsa.sigma.ot.domain;

import java.util.List;

/**
 * Estado del incidente, distinto del de su OT. EN_ESPERA_TERCERO viene del anteproyecto (CU-1 4b);
 * los demás nombres son propuestos.
 */
public enum EstadoIncidente {
  ABIERTO,
  EN_ESPERA_TERCERO,
  CON_OT,
  CERRADO_SIN_OT;

  /**
   * Los incidentes que entran por sí solos al historial de la instalación (HU-10): los que no
   * tienen OT. Los demás aparecen a través de su OT.
   */
  public static final List<EstadoIncidente> SIN_OT_EN_HISTORIAL =
      List.of(EN_ESPERA_TERCERO, CERRADO_SIN_OT);
}
