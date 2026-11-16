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

-- -----------------------------------------------------------------------------
-- Datos para la demo y las pruebas del sprint 2 (#81, #47). Solo se agrega: lo
-- de arriba queda como estaba, salvo DEV-006, que pasa a inactiva.
-- -----------------------------------------------------------------------------

INSERT INTO cuadrilla (nombre, zona) VALUES
                                       ('Cuadrilla Dev Este',  'Interbalnearia'),
                                       ('Cuadrilla Dev Oeste', 'Ruta 5')
  ON CONFLICT (nombre) DO NOTHING;

-- Misma contraseña que los de arriba: SigmaDev2026
INSERT INTO usuario (nombre, correo, password_hash, rol, id_cuadrilla)
SELECT u.nombre, u.correo, '$2y$10$LpiiWJG30C3mq1UV2qEDGuMfIvVNcrRw1ajCqrDfLJU/B/HQUbQbK',
       u.rol, c.id_cuadrilla
FROM (VALUES
        ('Operadora Dev 2', 'operador2.dev@sigma.test', 'OPERADOR', NULL),
        ('Técnico Este 1',  'tecnico3.dev@sigma.test',  'TECNICO',  'Cuadrilla Dev Este'),
        ('Técnico Este 2',  'tecnico4.dev@sigma.test',  'TECNICO',  'Cuadrilla Dev Este'),
        ('Técnico Oeste 1', 'tecnico5.dev@sigma.test',  'TECNICO',  'Cuadrilla Dev Oeste'),
        ('Técnico Oeste 2', 'tecnico6.dev@sigma.test',  'TECNICO',  'Cuadrilla Dev Oeste')
     ) AS u (nombre, correo, rol, cuadrilla)
       LEFT JOIN cuadrilla c ON c.nombre = u.cuadrilla
  ON CONFLICT (correo) DO NOTHING;

-- Organismo sin reglas de SLA: sus OTs quedan con «SLA sin definir» (CU-1 8a, #47)
INSERT INTO organismo (nombre) VALUES ('Organismo Dev sin SLA')
  ON CONFLICT (nombre) DO NOTHING;

INSERT INTO instalacion (codigo, nombre, tipo, direccion, ruta_zona, latitud, longitud, id_organismo)
SELECT 'DEV-007', 'Cruce Dev 07 (sin regla de SLA)', 'Semáforo', 'Km 12', 'Ruta 5',
       -34.790000, -56.200000, o.id_organismo
FROM organismo o
WHERE o.nombre = 'Organismo Dev sin SLA'
  ON CONFLICT (codigo) DO NOTHING;

-- Coordenadas aproximadas sobre las rutas nacionales, para que el mapa se vea repartido
INSERT INTO instalacion (codigo, nombre, tipo, direccion, ruta_zona, latitud, longitud, id_organismo)
SELECT i.codigo, i.nombre, i.tipo, i.direccion, i.ruta_zona, i.latitud, i.longitud, o.id_organismo
FROM organismo o
       CROSS JOIN (VALUES
                     ('DEV-008', 'Cruce Dev 08 · Ruta 1 km 70',          'Semáforo', 'Km 70',  'Ruta 1',         -34.630000, -56.640000),
                     ('DEV-009', 'Cruce Dev 09 · Ruta 1 km 121',         'Semáforo', 'Km 121', 'Ruta 1',         -34.340000, -57.240000),
                     ('DEV-010', 'Cruce Dev 10 · Ruta 1 km 150',         'Semáforo', 'Km 150', 'Ruta 1',         -34.380000, -57.550000),
                     ('DEV-011', 'Cruce Dev 11 · Ruta 1 km 175',         'Semáforo', 'Km 175', 'Ruta 1',         -34.450000, -57.800000),
                     ('DEV-012', 'Cruce Dev 12 · Ruta 5 km 20',          'Semáforo', 'Km 20',  'Ruta 5',         -34.730000, -56.220000),
                     ('DEV-013', 'Cruce Dev 13 · Ruta 5 km 35',          'Semáforo', 'Km 35',  'Ruta 5',         -34.620000, -56.250000),
                     ('DEV-014', 'Cruce Dev 14 · Ruta 5 km 50',          'Semáforo', 'Km 50',  'Ruta 5',         -34.530000, -56.280000),
                     ('DEV-015', 'Cruce Dev 15 · Ruta 5 km 97',          'Semáforo', 'Km 97',  'Ruta 5',         -34.120000, -56.220000),
                     ('DEV-016', 'Cruce Dev 16 · Ruta 8 km 32',          'Semáforo', 'Km 32',  'Ruta 8',         -34.720000, -55.960000),
                     ('DEV-017', 'Cruce Dev 17 · Ruta 8 km 55',          'Semáforo', 'Km 55',  'Ruta 8',         -34.670000, -55.720000),
                     ('DEV-018', 'Cruce Dev 18 · Ruta 8 km 75',          'Semáforo', 'Km 75',  'Ruta 8',         -34.560000, -55.550000),
                     ('DEV-019', 'Cruce Dev 19 · Ruta 8 km 120',         'Semáforo', 'Km 120', 'Ruta 8',         -34.380000, -55.240000),
                     ('DEV-020', 'Cruce Dev 20 · Ruta 9 km 100',         'Semáforo', 'Km 100', 'Ruta 9',         -34.790000, -55.230000),
                     ('DEV-021', 'Cruce Dev 21 · Ruta 9 km 132',         'Semáforo', 'Km 132', 'Ruta 9',         -34.790000, -54.920000),
                     ('DEV-022', 'Cruce Dev 22 · Ruta 9 km 175',         'Semáforo', 'Km 175', 'Ruta 9',         -34.680000, -54.620000),
                     ('DEV-023', 'Cruce Dev 23 · Ruta 9 km 210',         'Semáforo', 'Km 210', 'Ruta 9',         -34.480000, -54.340000),
                     ('DEV-024', 'Cruce Dev 24 · Interbalnearia km 22',  'Semáforo', 'Km 22',  'Interbalnearia', -34.840000, -55.990000),
                     ('DEV-025', 'Cruce Dev 25 · Interbalnearia km 45',  'Semáforo', 'Km 45',  'Interbalnearia', -34.770000, -55.760000),
                     ('DEV-026', 'Cruce Dev 26 · Interbalnearia km 62',  'Semáforo', 'Km 62',  'Interbalnearia', -34.790000, -55.570000),
                     ('DEV-027', 'Cruce Dev 27 · Interbalnearia km 80',  'Semáforo', 'Km 80',  'Interbalnearia', -34.800000, -55.380000)
) AS i (codigo, nombre, tipo, direccion, ruta_zona, latitud, longitud)
WHERE o.nombre = 'MTOP'
  ON CONFLICT (codigo) DO NOTHING;

-- DEV-006 inactiva, para probar que no se puede registrar un incidente en ella (CU-1 3a, #47)
UPDATE instalacion SET activa = FALSE WHERE codigo = 'DEV-006' AND activa;
