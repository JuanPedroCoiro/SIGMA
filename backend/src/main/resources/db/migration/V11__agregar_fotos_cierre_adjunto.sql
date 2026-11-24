-- =============================================================================
-- V11 — Fotos del cierre técnico (#90, HU-08, CU-3 pasos 4 y 7). El adjunto es de un incidente
-- (#71) o del cierre de una OT, nunca de los dos; la foto del cierre lleva su momento (Antes,
-- Durante o Después). Las fotos de varios cierres de la misma OT (si vuelve observada, HU-09) se
-- conservan todas.
-- =============================================================================
ALTER TABLE adjunto
  ALTER COLUMN id_incidente DROP NOT NULL,
    ADD COLUMN id_orden_trabajo BIGINT REFERENCES orden_trabajo (id_orden_trabajo),
    ADD COLUMN momento          VARCHAR(10),
    ADD CONSTRAINT ck_adjunto_origen
        CHECK ((id_incidente IS NULL) <> (id_orden_trabajo IS NULL)),
    ADD CONSTRAINT ck_adjunto_momento
        CHECK ((id_orden_trabajo IS NULL AND momento IS NULL)
            OR (id_orden_trabajo IS NOT NULL AND momento IN ('ANTES', 'DURANTE', 'DESPUES')));

CREATE INDEX ix_adjunto_orden_trabajo ON adjunto (id_orden_trabajo);
