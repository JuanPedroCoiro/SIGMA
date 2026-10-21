-- Ajustes al modelo de datos del sprint 1 (issue #28, revisión del PR #25)

-- 1. Fechas con zona horaria (Instant en Java)
ALTER TABLE organismo
    ALTER COLUMN creado_en     TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en TYPE TIMESTAMPTZ;

ALTER TABLE regla_sla
    ALTER COLUMN creado_en     TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en TYPE TIMESTAMPTZ;

ALTER TABLE categoria_falla
    ALTER COLUMN creado_en     TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en TYPE TIMESTAMPTZ;

ALTER TABLE instalacion
    ALTER COLUMN creado_en     TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en TYPE TIMESTAMPTZ;

ALTER TABLE cuadrilla
    ALTER COLUMN creado_en     TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en TYPE TIMESTAMPTZ;

ALTER TABLE usuario
    ALTER COLUMN creado_en       TYPE TIMESTAMPTZ,
    ALTER COLUMN modificado_en   TYPE TIMESTAMPTZ,
    ALTER COLUMN bloqueado_hasta TYPE TIMESTAMPTZ;

-- 2. Las cuadrillas se desactivan, no se borran
ALTER TABLE cuadrilla
    ADD COLUMN activa BOOLEAN NOT NULL DEFAULT TRUE;

-- 3. Prioridades (provisorias hasta definirlas con CIEMSA)
ALTER TABLE regla_sla
    ADD CONSTRAINT ck_regla_sla_prioridad
        CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA'));

ALTER TABLE categoria_falla
    ADD CONSTRAINT ck_categoria_falla_prioridad
        CHECK (prioridad_sugerida IN ('ALTA', 'MEDIA', 'BAJA'));

-- 4. Nombres explícitos para los UNIQUE
ALTER TABLE organismo       RENAME CONSTRAINT organismo_nombre_key       TO uq_organismo_nombre;
ALTER TABLE categoria_falla RENAME CONSTRAINT categoria_falla_nombre_key TO uq_categoria_falla_nombre;
ALTER TABLE instalacion     RENAME CONSTRAINT instalacion_codigo_key     TO uq_instalacion_codigo;
ALTER TABLE cuadrilla       RENAME CONSTRAINT cuadrilla_nombre_key       TO uq_cuadrilla_nombre;

-- 5. email pasa a correo (CU-13a), siempre en minúsculas
ALTER TABLE usuario RENAME COLUMN email TO correo;
ALTER TABLE usuario RENAME CONSTRAINT usuario_email_key TO uq_usuario_correo;
ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_correo_minusculas
        CHECK (correo = lower(correo));
