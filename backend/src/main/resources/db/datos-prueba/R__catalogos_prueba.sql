-- =============================================================================
-- Datos FICTICIOS de catálogos para desarrollo y tests (HU-02). Nunca se aplican
-- en el servidor: esta ubicación solo está en el perfil dev y en los tests.
-- Migración repetible: se vuelve a aplicar si cambia, por eso es idempotente.
-- Las reglas de SLA son provisorias hasta que CIEMSA confirme cómo se miden.
-- =============================================================================

INSERT INTO categoria_falla (nombre, prioridad_sugerida) VALUES
    ('Semáforo apagado',          'ALTA'),
    ('Lámpara quemada',           'MEDIA'),
    ('Falla de sincronización',   'MEDIA'),
    ('Pulsador peatonal roto',    'BAJA'),
    ('Daño en estructura',        'BAJA')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO regla_sla (id_organismo, prioridad, horas_respuesta, horas_resolucion)
SELECT o.id_organismo, r.prioridad, r.horas_respuesta, r.horas_resolucion
FROM organismo o
CROSS JOIN (VALUES
    ('ALTA',  2,  8),
    ('MEDIA', 8,  48),
    ('BAJA',  24, 120)
) AS r (prioridad, horas_respuesta, horas_resolucion)
WHERE o.nombre = 'MTOP'
ON CONFLICT ON CONSTRAINT uq_regla_sla_organismo_prioridad DO NOTHING;
