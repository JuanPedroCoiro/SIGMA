-- =============================================================================
-- V10 — Ejecución de la OT (#88, HU-07, CU-3): hora de inicio, cierre técnico con sus datos y el
-- identificador de operación que hace idempotente el cierre (R8).
--
-- iniciada_en y cerrada_en se guardan en la OT y no se sacan de la bitácora: cerrada_en es la base
-- del cumplimiento del SLA (CU-5) y los informes la consultan directo. iniciada_en es el primer
-- inicio; si la OT vuelve por una observación (HU-09), el cierre siguiente pisa los datos del
-- anterior y el historial queda en la bitácora.
-- =============================================================================
ALTER TABLE orden_trabajo
    ADD COLUMN iniciada_en          TIMESTAMPTZ,
    ADD COLUMN cerrada_en           TIMESTAMPTZ,
    ADD COLUMN diagnostico          VARCHAR(2000),
    ADD COLUMN solucion             VARCHAR(2000),
    ADD COLUMN observaciones_cierre VARCHAR(2000),
    ADD COLUMN id_operacion_cierre  UUID;

-- Las OTs que ya empezaron en bases locales (las armaron tests o pruebas a mano, sin endpoint):
-- también las pausadas desde En proceso, que al reanudar vuelven ahí.
UPDATE orden_trabajo
SET iniciada_en = modificado_en
WHERE iniciada_en IS NULL
  AND (estado = 'EN_PROCESO' OR (estado = 'EN_PAUSA' AND estado_anterior_pausa = 'EN_PROCESO'));

ALTER TABLE orden_trabajo
    ADD CONSTRAINT uq_orden_trabajo_id_operacion_cierre UNIQUE (id_operacion_cierre),
    ADD CONSTRAINT ck_orden_trabajo_inicio
        CHECK (estado <> 'EN_PROCESO' OR iniciada_en IS NOT NULL),
    ADD CONSTRAINT ck_orden_trabajo_cierre
        CHECK (estado NOT IN ('CERRADA_TECNICA', 'VALIDADA')
            OR (cerrada_en IS NOT NULL AND diagnostico IS NOT NULL AND solucion IS NOT NULL
                AND id_operacion_cierre IS NOT NULL));
