-- =====================================================================
-- Usuarios y departamentos de demostración
--   Internos (LDAP): la contraseña vive en el directorio (ldap/diseconstech.ldif),
--                    aquí solo se guardan roles, departamento y límite.
--   Externos (LOCAL): contraseña BCrypt. Contraseña demo para todos: Demo2026!
--
-- Límites pensados para demostrar la doble aprobación:
--   Orden de Q 30,000 aprobada por lgarcia (límite Q 10,000) -> queda pendiente
--   de nivel 2, que puede dar mperez (Q 50,000) o rlopez (Q 100,000).
-- =====================================================================
SET search_path TO oc;

INSERT INTO departamentos (codigo, nombre) VALUES
  ('TI',       'Tecnología'),
  ('COMPRAS',  'Compras'),
  ('FINANZAS', 'Finanzas'),
  ('GERENCIA', 'Gerencia General'),
  ('VENTAS',   'Ventas');

INSERT INTO usuarios (username, nombre_completo, email, tipo_autenticacion, password_hash, departamento_id, limite_aprobacion)
SELECT v.username, v.nombre, v.email, v.tipo, v.hash, d.id, v.limite
  FROM (VALUES
    ('jmorales',  'Jorge Morales',  'jmorales@diseconstech.local',  'LDAP',  NULL, 'TI',       NULL::numeric),
    ('lgarcia',   'Lucía García',   'lgarcia@diseconstech.local',   'LDAP',  NULL, 'COMPRAS',  10000.00),
    ('mperez',    'Mario Pérez',    'mperez@diseconstech.local',    'LDAP',  NULL, 'FINANZAS', 50000.00),
    ('rlopez',    'Rosa López',     'rlopez@diseconstech.local',    'LDAP',  NULL, 'GERENCIA', 100000.00),
    ('cramirez',  'Carlos Ramírez', 'cramirez@asesores.example.com', 'LOCAL',
        '$2a$10$xCiF4m/yL1u11wMusdU8D.kfZfY01Ki2EY4IUn9LNUkI/MxE/CE6S', 'VENTAS', NULL),
    ('asandoval', 'Ana Sandoval',   'asandoval@asesores.example.com', 'LOCAL',
        '$2a$10$xCiF4m/yL1u11wMusdU8D.kfZfY01Ki2EY4IUn9LNUkI/MxE/CE6S', 'VENTAS', NULL)
  ) AS v(username, nombre, email, tipo, hash, depto, limite)
  JOIN departamentos d ON d.codigo = v.depto;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id
  FROM (VALUES
    ('jmorales',  'ADMIN'),
    ('lgarcia',   'APROBADOR'),
    ('mperez',    'APROBADOR'),
    ('rlopez',    'ADMIN'),
    ('rlopez',    'APROBADOR'),
    ('cramirez',  'EXTERNO'),
    ('asandoval', 'EXTERNO')
  ) AS v(username, rol)
  JOIN usuarios u ON u.username = v.username
  JOIN roles r    ON r.codigo   = v.rol;
