-- =============================================================================
-- V13 — El técnico se cambia de cuadrilla (#94, HU-20): la bitácora suma la acción
-- CAMBIO_CUADRILLA sobre el usuario, con la cuadrilla anterior y la nueva. El cambio que hace la
-- Jefatura desde la edición del usuario sigue siendo MODIFICACION.
-- =============================================================================
ALTER TABLE registro_auditoria
DROP CONSTRAINT ck_registro_auditoria_accion;

ALTER TABLE registro_auditoria
  ADD CONSTRAINT ck_registro_auditoria_accion
    CHECK (accion IN ('REGISTRO', 'CIERRE_SIN_OT', 'GENERACION', 'CAMBIO_ESTADO',
                      'ASIGNACION', 'REASIGNACION', 'PAUSA', 'REANUDACION',
                      'LIBERACION', 'ANULACION', 'ALTA', 'MODIFICACION',
                      'RESTABLECIMIENTO_CONTRASENA', 'DESACTIVACION',
                      'ACTIVACION', 'CAMBIO_CUADRILLA'));
