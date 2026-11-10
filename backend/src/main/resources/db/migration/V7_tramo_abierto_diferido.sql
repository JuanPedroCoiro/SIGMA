-- =============================================================================
-- V7: «una OT a cargo de a lo sumo una cuadrilla a la vez» se verifica al confirmar
-- la transacción (HU-05, issue #41).
-- Al reasignar, Hibernate inserta el tramo nuevo antes de cerrar el anterior. Con el
-- índice único parcial de V6 eso falla aunque al terminar quede un solo tramo abierto.
-- La restricción de exclusión hace cumplir la misma regla, pero diferida al COMMIT.
-- =============================================================================

DROP INDEX uq_tramo_cuadrilla_ot_abierto;

ALTER TABLE tramo_cuadrilla_ot
  ADD CONSTRAINT uq_tramo_cuadrilla_ot_abierto
  EXCLUDE (id_orden_trabajo WITH =) WHERE (hasta IS NULL)
        DEFERRABLE INITIALLY DEFERRED;
