-- =====================================================================
-- Datos iniciales (necesarios para que la app funcione)
-- Los usuarios de la demo van aparte, no en esta migración.
-- =====================================================================
SET search_path TO oc;

INSERT INTO roles (codigo, nombre, descripcion) VALUES
  ('ADMIN',     'Administrador', 'Interno. Gestiona usuarios, catálogos y parámetros; ve todas las órdenes y reportes.'),
  ('APROBADOR', 'Aprobador',     'Interno. Aprueba o rechaza órdenes según su límite de aprobación.'),
  ('EXTERNO',   'Asesor',        'Externo. Crea y da seguimiento a sus propias órdenes; solo ve sus registros.');

INSERT INTO parametros (clave, valor, tipo_dato, descripcion) VALUES
  ('PLAZO_APROBACION_DIAS',  '2',   'ENTERO',  'Días máximos en Pendiente antes de considerarse vencida.'),
  ('MAX_INTENTOS_LOGIN',     '5',   'ENTERO',  'Intentos fallidos antes de bloquear una cuenta externa.'),
  ('MINUTOS_BLOQUEO',        '15',  'ENTERO',  'Minutos que dura el bloqueo por intentos fallidos.'),
  ('MONEDA',                 'GTQ', 'TEXTO',   'Moneda de la aplicación.');

INSERT INTO catalogo (tipo, codigo, descripcion, orden) VALUES
  ('MOTIVO_RECHAZO',     'MONTO_EXCESIVO',    'Monto fuera de presupuesto',                 1),
  ('MOTIVO_RECHAZO',     'DOC_INCOMPLETA',    'Documentación incompleta',                   2),
  ('MOTIVO_RECHAZO',     'PROV_NO_AUTORIZADO','Proveedor no autorizado',                    3),
  ('MOTIVO_RECHAZO',     'SIN_REG_SANITARIO', 'Producto sin registro sanitario vigente',    4),
  ('MOTIVO_RECHAZO',     'OTRO',              'Otro (ver comentario)',                      99),
  ('MOTIVO_CANCELACION', 'VENTA_CAIDA',       'El cliente desistió de la compra',           1),
  ('MOTIVO_CANCELACION', 'SIN_EXISTENCIA',    'Proveedor sin existencia',                   2),
  ('MOTIVO_CANCELACION', 'DUPLICADA',         'Orden duplicada',                            3),
  ('MOTIVO_CANCELACION', 'OTRO',              'Otro (ver comentario)',                      99),
  ('UNIDAD_MEDIDA',      'UNIDAD',            'Unidad',                                     1),
  ('UNIDAD_MEDIDA',      'CAJA',              'Caja',                                       2),
  ('UNIDAD_MEDIDA',      'FRASCO',            'Frasco',                                     3),
  ('UNIDAD_MEDIDA',      'BLISTER',           'Blíster',                                    4);
