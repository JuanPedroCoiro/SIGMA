package com.ciemsa.sigma.ot.domain;

/**
 * Estado del incidente, distinto del de su OT. EN_ESPERA_TERCERO viene del anteproyecto (CU-1 4b);
 * los demás nombres son propuestos.
 */
public enum EstadoIncidente {
  ABIERTO,
  EN_ESPERA_TERCERO,
  CON_OT,
  CERRADO_SIN_OT
}
