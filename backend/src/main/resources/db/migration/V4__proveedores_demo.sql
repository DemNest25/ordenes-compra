-- Proveedores de demostración (NIT ficticios)
SET search_path TO oc;

INSERT INTO proveedores (nit, razon_social, nombre_comercial, telefono, email, direccion) VALUES
  ('1234567-8', 'Distribuidora Farmacéutica del Sur, S.A.', 'FarmaSur',     '2222-1010', 'ventas@farmasur.example.com',    '12 calle 4-56 zona 9, Guatemala'),
  ('2345678-9', 'Laboratorios Centroamericanos, S.A.',      'LabCA',        '2233-2020', 'pedidos@labca.example.com',      'Km 15 carretera a El Salvador'),
  ('3456789-K', 'Insumos Médicos de Guatemala, S.A.',        'InsuMed',      '2244-3030', 'info@insumed.example.com',       '6a avenida 10-20 zona 4, Mixco'),
  ('4567890-1', 'Droguería Nacional, S.A.',                  'DroNal',       '2255-4040', 'compras@dronal.example.com',     'Calzada Roosevelt 22-43 zona 11'),
  ('5678901-2', 'Equipos Hospitalarios Maya, S.A.',          'EquiMaya',     '2266-5050', 'contacto@equimaya.example.com',  '20 calle 1-10 zona 10, Guatemala');
