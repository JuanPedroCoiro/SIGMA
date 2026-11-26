package com.ciemsa.sigma.auditoria;

/** Qué acción se registró (ck_registro_auditoria_accion). Una nueva suma su valor en la base. */
public enum AccionAuditada {
  // Incidente
  REGISTRO,
  CIERRE_SIN_OT,
  // Orden de trabajo
  GENERACION,
  CAMBIO_ESTADO,
  ASIGNACION,
  REASIGNACION,
  PAUSA,
  REANUDACION,
  LIBERACION,
  OBSERVACION,
  ANULACION,
  // Usuario y cuadrilla
  ALTA,
  MODIFICACION,
  RESTABLECIMIENTO_CONTRASENA,
  DESACTIVACION,
  ACTIVACION,
  // Usuario: el técnico se cambia de cuadrilla (HU-20)
  CAMBIO_CUADRILLA
}
