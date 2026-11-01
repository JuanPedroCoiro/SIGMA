-- =============================================================================
-- Datos FICTICIOS para levantar el entorno de desarrollo (issue #24): cuadrillas,
-- un usuario por rol e instalaciones. Solo se aplican con el perfil dev, nunca en
-- el servidor ni en los tests (los de HU-01 cuentan usuarios).
-- Migración repetible: se vuelve a aplicar si cambia, por eso es idempotente.
-- Si un correo, nombre o código ya existe (por ejemplo, creado a mano), se deja
-- como está. Credenciales documentadas en el README.
-- =============================================================================

INSERT INTO cuadrilla (nombre, zona) VALUES
                                       ('Cuadrilla Dev Norte', 'Ruta 9'),
                                       ('Cuadrilla Dev Sur',   'Ruta 1')
  ON CONFLICT (nombre) DO NOTHING;

-- Contraseña de todos: SigmaDev2026 (hash BCrypt, costo 10)
INSERT INTO usuario (nombre, correo, password_hash, rol, id_cuadrilla)
SELECT u.nombre, u.correo, '$2y$10$LpiiWJG30C3mq1UV2qEDGuMfIvVNcrRw1ajCqrDfLJU/B/HQUbQbK',
       u.rol, c.id_cuadrilla
FROM (VALUES
        ('Jefatura Dev',  'jefatura.dev@sigma.test',  'JEFATURA',        NULL),
        ('Operador Dev',  'operador.dev@sigma.test',  'OPERADOR',        NULL),
        ('Encargado Dev', 'stock.dev@sigma.test',     'ENCARGADO_STOCK', NULL),
        ('Técnico Norte', 'tecnico1.dev@sigma.test',  'TECNICO',         'Cuadrilla Dev Norte'),
        ('Técnico Sur',   'tecnico2.dev@sigma.test',  'TECNICO',         'Cuadrilla Dev Sur')
     ) AS u (nombre, correo, rol, cuadrilla)
       LEFT JOIN cuadrilla c ON c.nombre = u.cuadrilla
  ON CONFLICT (correo) DO NOTHING;

-- Prefijo DEV- para no chocar con la planilla de la demo (INST-...)
INSERT INTO instalacion (codigo, nombre, tipo, direccion, ruta_zona, latitud, longitud, id_organismo)
SELECT i.codigo, i.nombre, i.tipo, i.direccion, i.ruta_zona, i.latitud, i.longitud, o.id_organismo
FROM organismo o
       CROSS JOIN (VALUES
                     ('DEV-001', 'Cruce Dev 01', 'Semáforo', 'Km 21', 'Ruta 1', -34.780000, -56.250000),
                     ('DEV-002', 'Cruce Dev 02', 'Semáforo', 'Km 34', 'Ruta 1', -34.700000, -56.390000),
                     ('DEV-003', 'Cruce Dev 03', 'Semáforo', 'Km 48', 'Ruta 1', -34.620000, -56.530000),
                     ('DEV-004', 'Cruce Dev 04', 'Semáforo', 'Km 26', 'Ruta 9', -34.800000, -55.900000),
                     ('DEV-005', 'Cruce Dev 05', 'Semáforo', 'Km 40', 'Ruta 9', -34.760000, -55.760000),
                     ('DEV-006', 'Cruce Dev 06', 'Semáforo', 'Km 55', 'Ruta 9', -34.730000, -55.600000)
) AS i (codigo, nombre, tipo, direccion, ruta_zona, latitud, longitud)
WHERE o.nombre = 'MTOP'
  ON CONFLICT (codigo) DO NOTHING;
