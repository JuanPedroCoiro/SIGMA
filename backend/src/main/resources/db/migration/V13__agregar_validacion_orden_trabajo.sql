-- =============================================================================
-- V13 — Validar u observar una OT cerrada (#91, HU-09, CU-5). La Jefatura aprueba (Validada) u
-- observa con motivo (vuelve a Asignada, a la misma cuadrilla).
--
-- La OT guarda la última observación, que la cuadrilla ve en «Mis OTs» (4a); el historial de
-- observaciones queda en la bitácora (OBSERVACION). validada_en es la fecha del historial de la
-- instalación (CU-8). El cumplimiento del SLA no se guarda: sale de cerrada_en y fecha_limite.
-- =============================================================================
ALTER TABLE orden_trabajo
  ADD COLUMN motivo_observacion VARCHAR(500),
    ADD COLUMN observada_en       TIMESTAMPTZ,
    ADD COLUMN validada_en        TIMESTAMPTZ;

-- Por si alguna base local tiene OTs validadas a mano (no había endpoint).
UPDATE orden_trabajo
SET validada_en = modificado_en
WHERE estado = 'VALIDADA'
  AND validada_en IS NULL;

ALTER TABLE orden_trabajo
  ADD CONSTRAINT ck_orden_trabajo_observacion
    CHECK ((motivo_observacion IS NULL) = (observada_en IS NULL)),
    ADD CONSTRAINT ck_orden_trabajo_validacion
        CHECK (estado <> 'VALIDADA' OR validada_en IS NOT NULL);

-- Bitácora: la observación con su motivo. Validar queda como CAMBIO_ESTADO a VALIDADA.
ALTER TABLE registro_auditoria
DROP CONSTRAINT ck_registro_auditoria_accion;

ALTER TABLE registro_auditoria
  ADD CONSTRAINT ck_registro_auditoria_accion
    CHECK (accion IN ('REGISTRO', 'CIERRE_SIN_OT', 'GENERACION', 'CAMBIO_ESTADO',
                      'ASIGNACION', 'REASIGNACION', 'PAUSA', 'REANUDACION',
                      'LIBERACION', 'OBSERVACION', 'ANULACION', 'ALTA', 'MODIFICACION',
                      'RESTABLECIMIENTO_CONTRASENA', 'DESACTIVACION',
                      'ACTIVACION'));
