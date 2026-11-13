-- =====================================================================
-- Los 4 usuarios internos tienen acceso de administrador (punto 1 del documento).
-- lgarcia y mperez reciben ADMIN y conservan APROBADOR con sus límites.
-- Migración nueva: V1 a V4 ya están aplicadas en Render y no se modifican
-- (Flyway rechaza cambios en migraciones aplicadas por checksum).
-- =====================================================================
SET search_path TO oc;

INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT u.id, r.id
  FROM usuarios u
  JOIN roles r ON r.codigo = 'ADMIN'
 WHERE u.username IN ('lgarcia', 'mperez')
ON CONFLICT (usuario_id, rol_id) DO NOTHING;
