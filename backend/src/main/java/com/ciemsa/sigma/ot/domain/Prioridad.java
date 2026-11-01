package com.ciemsa.sigma.ot.domain;

/**
 * Prioridad de un incidente y de una regla de SLA (RN-01, RN-02). Provisoria hasta definirla con
 * CIEMSA; los CHECK de regla_sla, categoria_falla e incidente admiten los mismos valores.
 */
public enum Prioridad {
  ALTA,
  MEDIA,
  BAJA
}
