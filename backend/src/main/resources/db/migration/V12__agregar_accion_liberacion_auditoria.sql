-- =============================================================================
-- V12 — Liberar una OT (#93, HU-20): la bitácora suma la acción LIBERACION, con la cuadrilla que
-- tenía la OT como valor anterior. Tomar no suma acción: queda como ASIGNACION con el usuario del
-- técnico.
-- =============================================================================
ALTER TABLE registro_auditoria
DROP CONSTRAINT ck_registro_auditoria_accion;

ALTER TABLE registro_auditoria
  ADD CONSTRAINT ck_registro_auditoria_accion
    CHECK (accion IN ('REGISTRO', 'CIERRE_SIN_OT', 'GENERACION', 'CAMBIO_ESTADO',
                      'ASIGNACION', 'REASIGNACION', 'PAUSA', 'REANUDACION',
                      'LIBERACION', 'ANULACION', 'ALTA', 'MODIFICACION',
                      'RESTABLECIMIENTO_CONTRASENA', 'DESACTIVACION',
                      'ACTIVACION'));
