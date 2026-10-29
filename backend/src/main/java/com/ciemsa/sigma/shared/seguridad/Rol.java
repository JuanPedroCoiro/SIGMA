package com.ciemsa.sigma.shared.seguridad;

/**
 * Roles del sistema (RF-20). Se guardan como texto; el CHECK ck_usuario_rol admite los mismos
 * valores.
 */
public enum Rol {
  JEFATURA,
  TECNICO,
  OPERADOR,
  ENCARGADO_STOCK;

  /** Solo el técnico trabaja dentro de una cuadrilla (CU-13a 4b). */
  public boolean requiereCuadrilla() {
    return this == TECNICO;
  }
}
