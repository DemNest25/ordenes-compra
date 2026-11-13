-- =====================================================================
-- Webapp Órdenes de Compra - Esquema inicial
-- PostgreSQL 16 | Migración Flyway V1
--
-- Reglas que este esquema hace cumplir desde la base de datos
-- (además de lo que valide el backend, como segunda línea de defensa):
--   * Nunca hay borrado físico: todo se marca con "eliminado".
--   * Bitácora e historial de estados son append-only (no UPDATE/DELETE).
--   * Solo se permiten las transiciones de estado acordadas.
--   * El aprobador no puede ser el creador, y los dos niveles de
--     aprobación deben ser personas distintas.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS oc;
SET search_path TO oc;

-- ---------------------------------------------------------------------
-- MAESTROS
-- ---------------------------------------------------------------------

CREATE TABLE departamentos (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo          VARCHAR(20)  NOT NULL UNIQUE,
    nombre          VARCHAR(100) NOT NULL,
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminado       BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    creado_por      BIGINT,
    actualizado_en  TIMESTAMPTZ,
    actualizado_por BIGINT
);

CREATE TABLE proveedores (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nit              VARCHAR(20)  NOT NULL UNIQUE,
    razon_social     VARCHAR(200) NOT NULL,
    nombre_comercial VARCHAR(200),
    telefono         VARCHAR(20),
    email            VARCHAR(150),
    direccion        VARCHAR(300),
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminado        BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    creado_por       BIGINT,
    actualizado_en   TIMESTAMPTZ,
    actualizado_por  BIGINT
);

-- Catálogo genérico: solo para listas simples (motivos de rechazo,
-- motivos de cancelación, unidades de medida...). Para crecer basta
-- con agregar filas con un "tipo" nuevo, sin tocar el esquema.
CREATE TABLE catalogo (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo        VARCHAR(50)  NOT NULL,
    codigo      VARCHAR(50)  NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    orden       SMALLINT     NOT NULL DEFAULT 0,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminado   BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tipo, codigo)
);

-- Valores configurables sin tocar código (plazo de aprobación, etc.)
CREATE TABLE parametros (
    clave           VARCHAR(60)  PRIMARY KEY,
    valor           VARCHAR(200) NOT NULL,
    tipo_dato       VARCHAR(20)  NOT NULL CHECK (tipo_dato IN ('ENTERO','DECIMAL','TEXTO','BOOLEANO')),
    descripcion     VARCHAR(300) NOT NULL,
    actualizado_en  TIMESTAMPTZ,
    actualizado_por BIGINT
);

-- ---------------------------------------------------------------------
-- SEGURIDAD
-- ---------------------------------------------------------------------

CREATE TABLE roles (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL UNIQUE,   -- ADMIN, APROBADOR, EXTERNO
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(300)
);

CREATE TABLE usuarios (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username              VARCHAR(100) NOT NULL UNIQUE,
    nombre_completo       VARCHAR(150) NOT NULL,
    email                 VARCHAR(150) NOT NULL UNIQUE,
    -- LDAP = interno (valida contra el directorio corporativo)
    -- LOCAL = externo (contraseña propia de la app, hash BCrypt)
    tipo_autenticacion    VARCHAR(10)  NOT NULL CHECK (tipo_autenticacion IN ('LDAP','LOCAL')),
    password_hash         VARCHAR(100),
    departamento_id       BIGINT REFERENCES departamentos(id),
    limite_aprobacion     NUMERIC(18,2) CHECK (limite_aprobacion >= 0),
    intentos_fallidos     SMALLINT     NOT NULL DEFAULT 0,
    bloqueado_hasta       TIMESTAMPTZ,
    debe_cambiar_password BOOLEAN      NOT NULL DEFAULT FALSE,
    ultimo_acceso         TIMESTAMPTZ,
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminado             BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    creado_por            BIGINT,
    actualizado_en        TIMESTAMPTZ,
    actualizado_por       BIGINT,
    -- Un interno nunca guarda contraseña aquí; un externo siempre.
    CONSTRAINT ck_usuarios_password CHECK (
        (tipo_autenticacion = 'LOCAL' AND password_hash IS NOT NULL) OR
        (tipo_autenticacion = 'LDAP'  AND password_hash IS NULL)
    )
);

CREATE TABLE usuario_rol (
    usuario_id   BIGINT      NOT NULL REFERENCES usuarios(id),
    rol_id       BIGINT      NOT NULL REFERENCES roles(id),
    asignado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    asignado_por BIGINT      REFERENCES usuarios(id),
    PRIMARY KEY (usuario_id, rol_id)
);

-- ---------------------------------------------------------------------
-- OPERACIÓN
-- ---------------------------------------------------------------------

CREATE SEQUENCE seq_numero_orden START 1;

CREATE TABLE ordenes (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero                      VARCHAR(20) NOT NULL UNIQUE
                                DEFAULT ('OC-' || to_char(now(), 'YYYY') || '-' ||
                                         lpad(nextval('oc.seq_numero_orden')::text, 6, '0')),
    creador_id                  BIGINT       NOT NULL REFERENCES usuarios(id),
    departamento_id             BIGINT       NOT NULL REFERENCES departamentos(id),
    proveedor_id                BIGINT       NOT NULL REFERENCES proveedores(id),
    estado                      VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR'
                                CHECK (estado IN ('BORRADOR','PENDIENTE','APROBADA',
                                                  'RECHAZADA','CERRADA','CANCELADA')),
    descripcion                 VARCHAR(500) NOT NULL,
    observaciones               TEXT,
    moneda                      CHAR(3)       NOT NULL DEFAULT 'GTQ',
    monto_total                 NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (monto_total >= 0),
    requiere_segunda_aprobacion BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_envio                 TIMESTAMPTZ,  -- último envío a aprobación (base para "vencidas")
    fecha_resolucion            TIMESTAMPTZ,  -- aprobación final o rechazo
    fecha_cierre                TIMESTAMPTZ,
    fecha_cancelacion           TIMESTAMPTZ,
    motivo_cancelacion_id       BIGINT REFERENCES catalogo(id),
    comentario_cancelacion      VARCHAR(500),
    version                     INTEGER      NOT NULL DEFAULT 0,  -- bloqueo optimista (@Version)
    eliminado                   BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en              TIMESTAMPTZ,
    actualizado_por             BIGINT REFERENCES usuarios(id)
);

CREATE TABLE orden_detalle (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orden_id        BIGINT        NOT NULL REFERENCES ordenes(id),
    linea           SMALLINT      NOT NULL,
    descripcion     VARCHAR(300)  NOT NULL,
    unidad_medida   VARCHAR(30)   NOT NULL DEFAULT 'UNIDAD',
    cantidad        NUMERIC(12,2) NOT NULL CHECK (cantidad > 0),
    precio_unitario NUMERIC(18,2) NOT NULL CHECK (precio_unitario >= 0),
    -- Calculado por la BD: nadie puede guardar un subtotal "inventado"
    subtotal        NUMERIC(18,2) GENERATED ALWAYS AS (round(cantidad * precio_unitario, 2)) STORED,
    eliminado       BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ
);
-- Número de línea único solo entre líneas vivas
CREATE UNIQUE INDEX ux_detalle_linea ON orden_detalle (orden_id, linea) WHERE NOT eliminado;

CREATE TABLE aprobaciones (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orden_id          BIGINT        NOT NULL REFERENCES ordenes(id),
    aprobador_id      BIGINT        NOT NULL REFERENCES usuarios(id),
    nivel             SMALLINT      NOT NULL CHECK (nivel IN (1,2)),
    decision          VARCHAR(10)   NOT NULL CHECK (decision IN ('APROBADA','RECHAZADA')),
    motivo_rechazo_id BIGINT        REFERENCES catalogo(id),
    comentario        VARCHAR(500),
    -- Foto del momento: si después cambia el límite o el monto, queda la evidencia
    monto_orden       NUMERIC(18,2) NOT NULL,
    limite_aprobador  NUMERIC(18,2) NOT NULL,
    fecha             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- FALSE cuando la orden vuelve a Borrador después de aprobada
    vigente           BOOLEAN       NOT NULL DEFAULT TRUE,
    anulada_en        TIMESTAMPTZ,
    anulada_por       BIGINT REFERENCES usuarios(id),
    CONSTRAINT ck_aprob_rechazo_motivo CHECK (decision = 'APROBADA' OR motivo_rechazo_id IS NOT NULL)
);
-- Solo una decisión vigente por nivel
CREATE UNIQUE INDEX ux_aprob_nivel_vigente ON aprobaciones (orden_id, nivel) WHERE vigente;

CREATE TABLE orden_historial_estado (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    orden_id        BIGINT       NOT NULL REFERENCES ordenes(id),
    estado_anterior VARCHAR(20),               -- NULL = creación
    estado_nuevo    VARCHAR(20)  NOT NULL,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios(id),
    fecha           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    comentario      VARCHAR(500)
);

-- ---------------------------------------------------------------------
-- AUDITORÍA
-- ---------------------------------------------------------------------

CREATE TABLE bitacora (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usuario_id         BIGINT REFERENCES usuarios(id),
    username           VARCHAR(100) NOT NULL,   -- copia: sobrevive aunque cambie el usuario
    accion             VARCHAR(20)  NOT NULL CHECK (accion IN
                       ('CREAR','MODIFICAR','ELIMINAR','CAMBIO_ESTADO','APROBAR','RECHAZAR',
                        'LOGIN','LOGIN_FALLIDO','LOGOUT','BLOQUEO')),
    entidad            VARCHAR(50)  NOT NULL,   -- 'ordenes', 'usuarios', ...
    entidad_id         BIGINT,
    valores_anteriores JSONB,
    valores_nuevos     JSONB,
    ip                 VARCHAR(45),
    user_agent         VARCHAR(300)
);

-- ---------------------------------------------------------------------
-- ÍNDICES PARA REPORTES Y CONSULTAS FRECUENTES
-- ---------------------------------------------------------------------

CREATE INDEX ix_ordenes_creador      ON ordenes (creador_id)      WHERE NOT eliminado;
CREATE INDEX ix_ordenes_estado       ON ordenes (estado)          WHERE NOT eliminado;
CREATE INDEX ix_ordenes_departamento ON ordenes (departamento_id) WHERE NOT eliminado;
CREATE INDEX ix_ordenes_proveedor    ON ordenes (proveedor_id)    WHERE NOT eliminado;
CREATE INDEX ix_ordenes_creado_en    ON ordenes (creado_en);
CREATE INDEX ix_detalle_orden        ON orden_detalle (orden_id);
CREATE INDEX ix_aprob_aprobador      ON aprobaciones (aprobador_id, fecha);
CREATE INDEX ix_aprob_orden          ON aprobaciones (orden_id);
CREATE INDEX ix_historial_orden      ON orden_historial_estado (orden_id, fecha);
CREATE INDEX ix_bitacora_entidad     ON bitacora (entidad, entidad_id);
CREATE INDEX ix_bitacora_fecha       ON bitacora (fecha);
CREATE INDEX ix_bitacora_usuario     ON bitacora (usuario_id);

-- ---------------------------------------------------------------------
-- REGLAS DE NEGOCIO EN LA BASE DE DATOS (defensa en profundidad)
-- ---------------------------------------------------------------------

-- 1) Nunca DELETE físico en tablas de negocio
CREATE FUNCTION fn_prohibir_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Borrado físico prohibido en %. Use borrado lógico (eliminado = true).', TG_TABLE_NAME
        USING ERRCODE = 'P0001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_no_delete BEFORE DELETE ON departamentos FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON proveedores   FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON catalogo      FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON usuarios      FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON ordenes       FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON orden_detalle FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();
CREATE TRIGGER tg_no_delete BEFORE DELETE ON aprobaciones  FOR EACH ROW EXECUTE FUNCTION fn_prohibir_delete();

-- 2) Bitácora e historial: solo INSERT (append-only)
CREATE FUNCTION fn_solo_insert() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de solo inserción: no se permite %.', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'P0001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_append_only BEFORE UPDATE OR DELETE ON bitacora
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insert();
CREATE TRIGGER tg_append_only BEFORE UPDATE OR DELETE ON orden_historial_estado
    FOR EACH ROW EXECUTE FUNCTION fn_solo_insert();

-- 3) Solo transiciones de estado permitidas
--    (QUIÉN puede hacer cada una lo valida el backend según el rol)
CREATE FUNCTION fn_validar_transicion() RETURNS trigger AS $$
BEGIN
    IF NEW.estado IS DISTINCT FROM OLD.estado THEN
        IF NOT ( (OLD.estado = 'BORRADOR'  AND NEW.estado IN ('PENDIENTE','CANCELADA'))
              OR (OLD.estado = 'PENDIENTE' AND NEW.estado IN ('BORRADOR','CANCELADA','APROBADA','RECHAZADA'))
              OR (OLD.estado = 'RECHAZADA' AND NEW.estado = 'BORRADOR')
              OR (OLD.estado = 'APROBADA'  AND NEW.estado IN ('CERRADA','CANCELADA','BORRADOR')) ) THEN
            RAISE EXCEPTION 'Transición de estado no permitida: % -> %', OLD.estado, NEW.estado
                USING ERRCODE = 'P0001';
        END IF;

        IF NEW.estado = 'CANCELADA' AND OLD.estado = 'APROBADA' AND NEW.motivo_cancelacion_id IS NULL THEN
            RAISE EXCEPTION 'Cancelar una orden aprobada requiere motivo de cancelación'
                USING ERRCODE = 'P0001';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_transicion_estado BEFORE UPDATE OF estado ON ordenes
    FOR EACH ROW EXECUTE FUNCTION fn_validar_transicion();

-- 4) Segregación de funciones en aprobaciones
CREATE FUNCTION fn_validar_aprobador() RETURNS trigger AS $$
DECLARE
    v_creador BIGINT;
BEGIN
    SELECT creador_id INTO v_creador FROM oc.ordenes WHERE id = NEW.orden_id;

    IF NEW.aprobador_id = v_creador THEN
        RAISE EXCEPTION 'El creador de la orden no puede aprobarla ni rechazarla'
            USING ERRCODE = 'P0001';
    END IF;

    IF NEW.nivel = 2 AND EXISTS (
        SELECT 1 FROM oc.aprobaciones
         WHERE orden_id = NEW.orden_id AND nivel = 1 AND vigente
           AND aprobador_id = NEW.aprobador_id) THEN
        RAISE EXCEPTION 'El segundo aprobador debe ser una persona distinta al primero'
            USING ERRCODE = 'P0001';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_validar_aprobador BEFORE INSERT ON aprobaciones
    FOR EACH ROW EXECUTE FUNCTION fn_validar_aprobador();
