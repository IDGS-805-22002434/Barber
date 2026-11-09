-- ============================================================
-- BarberManager - Esquema MySQL integrado
-- Integra el catálogo preparado por el equipo con las tablas
-- necesarias para autenticación por roles y requerimientos RF01-RF08.
-- Recomendado: MySQL 8.0.16+ (por CHECK constraints).
-- Importar en una base nueva con:
--   mysql -u root -p barbermanager < schema.sql
-- El servidor crea la base (DB_NAME) y la selecciona antes de ejecutar
-- este script, por lo que aquí no se incluye CREATE DATABASE ni USE.
--
-- IMPORTANTE:
-- 1) Contraseñas: guardar únicamente password_hash + password_salt.
--    El backend debe generarlos con un algoritmo seguro (p. ej. Argon2id
--    o PBKDF2). Este script NO inserta credenciales de administrador.
-- 2) La app Kotlin revisada usa Room/SQLite local. Este archivo define
--    MySQL en servidor; para conectarlo a Android se necesita una API HTTPS.
-- 3) Precios monetarios se almacenan en CENTAVOS.
-- ============================================================

-- ------------------------------------------------------------
-- Catálogo de categorías de servicios
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorias (
  id       BIGINT NOT NULL AUTO_INCREMENT,
  nombre   VARCHAR(50) NOT NULL,
  icono    VARCHAR(50) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categoria_nombre (nombre)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Servicios del catálogo
-- Precio en centavos: 12000 = $120.00 MXN
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS servicios (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  nombre          VARCHAR(100) NOT NULL,
  descripcion     VARCHAR(255) NOT NULL DEFAULT '',
  categoria_id    BIGINT NOT NULL,
  precio_centavos BIGINT NOT NULL,
  activo          TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_servicio_categoria_nombre (categoria_id, nombre),
  KEY ix_servicios_categoria (categoria_id),
  KEY ix_servicios_activo (activo),
  CONSTRAINT fk_servicios_categoria
    FOREIGN KEY (categoria_id) REFERENCES categorias (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_servicios_precio CHECK (precio_centavos >= 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Barberos
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS barberos (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  nombre            VARCHAR(100) NOT NULL,
  anios_experiencia INT NOT NULL DEFAULT 0,
  activo            TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_barberos_nombre (nombre),
  KEY ix_barberos_activo (activo),
  CONSTRAINT chk_barberos_experiencia CHECK (anios_experiencia >= 0)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Catálogo de especialidades y relación N:N barbero-especialidad
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS especialidades (
  id     BIGINT NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_especialidad_nombre (nombre)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS barbero_especialidad (
  barbero_id      BIGINT NOT NULL,
  especialidad_id BIGINT NOT NULL,
  PRIMARY KEY (barbero_id, especialidad_id),
  KEY ix_be_especialidad (especialidad_id),
  CONSTRAINT fk_be_barbero
    FOREIGN KEY (barbero_id) REFERENCES barberos (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_be_especialidad
    FOREIGN KEY (especialidad_id) REFERENCES especialidades (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- NUEVO: usuarios y roles para login (RF01, RNF05-RNF07)
-- Los clientes se pueden registrar desde la app; el registro público
-- SIEMPRE debe asignar CLIENTE. Solo un proceso autorizado crea ADMINISTRADOR.
-- ============================================================
CREATE TABLE IF NOT EXISTS usuarios (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  nombre_completo    VARCHAR(150) NOT NULL,
  correo             VARCHAR(254) NOT NULL,
  telefono           VARCHAR(25) NULL,
  password_hash      VARCHAR(255) NOT NULL,
  password_salt      VARCHAR(255) NOT NULL,
  rol                ENUM('CLIENTE', 'ADMINISTRADOR') NOT NULL DEFAULT 'CLIENTE',
  activo             TINYINT(1) NOT NULL DEFAULT 1,
  creado_en          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_usuarios_correo (correo),
  KEY ix_usuarios_rol_activo (rol, activo)
) ENGINE=InnoDB;

-- Solicitudes para recuperación por correo (RF01.03).
-- Solo se almacena un hash del token temporal, nunca el token en claro.
CREATE TABLE IF NOT EXISTS recuperacion_contrasena (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id     BIGINT NOT NULL,
  token_hash     VARCHAR(255) NOT NULL,
  expira_en      DATETIME NOT NULL,
  utilizado_en   DATETIME NULL,
  creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_recuperacion_token_hash (token_hash),
  KEY ix_recuperacion_usuario (usuario_id),
  KEY ix_recuperacion_expiracion (expira_en),
  CONSTRAINT fk_recuperacion_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- NUEVO: horarios y bloqueos de los barberos (RF04)
-- dia_semana usa la convención de Android Calendar: 1=domingo ... 7=sábado.
-- ============================================================
CREATE TABLE IF NOT EXISTS barbero_horarios (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  barbero_id              BIGINT NOT NULL,
  dia_semana              TINYINT NOT NULL,
  hora_inicio             TIME NOT NULL,
  hora_fin                TIME NOT NULL,
  duracion_turno_minutos  SMALLINT NOT NULL DEFAULT 30,
  activo                  TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_horario_barbero_dia (barbero_id, dia_semana),
  KEY ix_horarios_activos (activo, dia_semana),
  CONSTRAINT fk_horarios_barbero
    FOREIGN KEY (barbero_id) REFERENCES barberos (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT chk_horario_dia CHECK (dia_semana BETWEEN 1 AND 7),
  CONSTRAINT chk_horario_rango CHECK (hora_fin > hora_inicio),
  CONSTRAINT chk_horario_duracion CHECK (duracion_turno_minutos > 0)
) ENGINE=InnoDB;

-- Si hora_inicio y hora_fin son NULL, el bloqueo representa todo el día.
CREATE TABLE IF NOT EXISTS barbero_bloqueos (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  barbero_id      BIGINT NOT NULL,
  fecha           DATE NOT NULL,
  hora_inicio     TIME NULL,
  hora_fin        TIME NULL,
  motivo          VARCHAR(255) NOT NULL DEFAULT '',
  creado_por      BIGINT NULL,
  creado_en       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_bloqueos_barbero_fecha (barbero_id, fecha),
  KEY ix_bloqueos_creado_por (creado_por),
  CONSTRAINT fk_bloqueos_barbero
    FOREIGN KEY (barbero_id) REFERENCES barberos (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_bloqueos_usuario
    FOREIGN KEY (creado_por) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_bloqueo_rango CHECK (
    (hora_inicio IS NULL AND hora_fin IS NULL) OR
    (hora_inicio IS NOT NULL AND hora_fin IS NOT NULL AND hora_fin > hora_inicio)
  )
) ENGINE=InnoDB;

-- ============================================================
-- NUEVO: citas, historial, consultas de servicios recibidos (RF05-RF06, RF08)
-- Se guarda precio y nombre del servicio como snapshot para conservar
-- los datos históricos aunque después el catálogo sea editado.
-- ============================================================
CREATE TABLE IF NOT EXISTS citas (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  cliente_id            BIGINT NOT NULL,
  barbero_id            BIGINT NOT NULL,
  servicio_id           BIGINT NOT NULL,
  fecha                 DATE NOT NULL,
  hora_inicio           TIME NOT NULL,
  hora_fin              TIME NOT NULL,
  estado                ENUM('PENDIENTE','CONFIRMADA','COMPLETADA','CANCELADA','REPROGRAMADA')
                        NOT NULL DEFAULT 'PENDIENTE',
  nombre_servicio       VARCHAR(100) NOT NULL,
  precio_centavos       BIGINT NOT NULL,
  nombre_cliente        VARCHAR(150) NOT NULL,
  telefono_cliente      VARCHAR(25) NULL,
  notas                 VARCHAR(500) NULL,
  creada_en             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizada_en        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cita_barbero_fecha_inicio (barbero_id, fecha, hora_inicio),
  KEY ix_citas_cliente_fecha (cliente_id, fecha),
  KEY ix_citas_barbero_fecha_estado (barbero_id, fecha, estado),
  KEY ix_citas_servicio (servicio_id),
  CONSTRAINT fk_citas_cliente
    FOREIGN KEY (cliente_id) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_citas_barbero
    FOREIGN KEY (barbero_id) REFERENCES barberos (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_citas_servicio
    FOREIGN KEY (servicio_id) REFERENCES servicios (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_citas_horario CHECK (hora_fin > hora_inicio),
  CONSTRAINT chk_citas_precio CHECK (precio_centavos >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS historial_citas (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  cita_id               BIGINT NOT NULL,
  cambiado_por          BIGINT NULL,
  tipo_cambio           ENUM('CREADA','REPROGRAMADA','CANCELADA','ESTADO_CAMBIADO') NOT NULL,
  fecha_anterior        DATE NULL,
  hora_inicio_anterior  TIME NULL,
  hora_fin_anterior     TIME NULL,
  fecha_nueva           DATE NULL,
  hora_inicio_nueva     TIME NULL,
  hora_fin_nueva        TIME NULL,
  motivo                VARCHAR(500) NULL,
  cambiado_en           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_historial_cita (cita_id, cambiado_en),
  KEY ix_historial_usuario (cambiado_por),
  CONSTRAINT fk_historial_cita
    FOREIGN KEY (cita_id) REFERENCES citas (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_historial_usuario
    FOREIGN KEY (cambiado_por) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

-- ============================================================
-- NUEVO: promociones, cupones y redenciones (RF08)
-- ============================================================
CREATE TABLE IF NOT EXISTS promociones (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  titulo         VARCHAR(120) NOT NULL,
  descripcion    VARCHAR(500) NOT NULL DEFAULT '',
  condiciones    VARCHAR(1000) NOT NULL DEFAULT '',
  inicia_en      DATETIME NOT NULL,
  termina_en     DATETIME NOT NULL,
  activo         TINYINT(1) NOT NULL DEFAULT 1,
  creada_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_promociones_vigencia (activo, inicia_en, termina_en),
  CONSTRAINT chk_promocion_fechas CHECK (termina_en > inicia_en)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cupones (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  promocion_id       BIGINT NULL,
  codigo             VARCHAR(50) NOT NULL,
  tipo_descuento     ENUM('PERCENT','AMOUNT_CENTS') NOT NULL,
  valor_descuento    BIGINT NOT NULL,
  condiciones        VARCHAR(1000) NOT NULL DEFAULT '',
  inicia_en          DATETIME NOT NULL,
  termina_en         DATETIME NOT NULL,
  max_usos           INT NULL,
  usos_actuales      INT NOT NULL DEFAULT 0,
  activo             TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cupon_codigo (codigo),
  KEY ix_cupon_promocion (promocion_id),
  KEY ix_cupon_vigencia (activo, inicia_en, termina_en),
  CONSTRAINT fk_cupon_promocion
    FOREIGN KEY (promocion_id) REFERENCES promociones (id)
    ON UPDATE CASCADE ON DELETE SET NULL,
  CONSTRAINT chk_cupon_valor CHECK (valor_descuento > 0),
  CONSTRAINT chk_cupon_usos CHECK (usos_actuales >= 0 AND (max_usos IS NULL OR max_usos >= 0)),
  CONSTRAINT chk_cupon_fechas CHECK (termina_en > inicia_en)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cupones_usados (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  cupon_id            BIGINT NOT NULL,
  usuario_id          BIGINT NOT NULL,
  cita_id             BIGINT NOT NULL,
  descuento_centavos  BIGINT NOT NULL,
  aplicado_en         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cupon_usuario_cita (cupon_id, usuario_id, cita_id),
  KEY ix_cupones_usados_usuario (usuario_id, aplicado_en),
  CONSTRAINT fk_cupon_usado_cupon
    FOREIGN KEY (cupon_id) REFERENCES cupones (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_cupon_usado_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_cupon_usado_cita
    FOREIGN KEY (cita_id) REFERENCES citas (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_cupon_descuento CHECK (descuento_centavos >= 0)
) ENGINE=InnoDB;

-- ============================================================
-- NUEVO: notificaciones y dispositivos FCM (RF07)
-- ============================================================
CREATE TABLE IF NOT EXISTS notificaciones (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id     BIGINT NOT NULL,
  cita_id        BIGINT NULL,
  titulo         VARCHAR(150) NOT NULL,
  mensaje        VARCHAR(1000) NOT NULL,
  tipo           VARCHAR(50) NOT NULL,
  estado         ENUM('PENDIENTE','ENVIADA','FALLIDA') NOT NULL DEFAULT 'PENDIENTE',
  creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  enviado_en     DATETIME NULL,
  PRIMARY KEY (id),
  KEY ix_notificaciones_usuario (usuario_id, creado_en),
  KEY ix_notificaciones_cita (cita_id),
  KEY ix_notificaciones_estado (estado, creado_en),
  CONSTRAINT fk_notificaciones_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_notificaciones_cita
    FOREIGN KEY (cita_id) REFERENCES citas (id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS dispositivos_fcm (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  usuario_id     BIGINT NOT NULL,
  token_fcm      VARCHAR(512) NOT NULL,
  activo         TINYINT(1) NOT NULL DEFAULT 1,
  registrado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_dispositivo_token (token_fcm),
  KEY ix_dispositivos_usuario (usuario_id, activo),
  CONSTRAINT fk_dispositivos_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- Semillas iniciales del catálogo (se pueden ejecutar otra vez).
-- INSERT IGNORE evita duplicar categorías, servicios y especialidades.
-- ============================================================
INSERT IGNORE INTO categorias (nombre, icono) VALUES
  ('CORTES', 'content_cut'),
  ('BARBA',  'man'),
  ('COMBOS', 'style'),
  ('CEJAS',  'face'),
  ('COLOR',  'palette');

INSERT INTO servicios (nombre, descripcion, categoria_id, precio_centavos, activo)
SELECT datos.nombre, datos.descripcion, c.id, datos.precio_centavos, 1
FROM (
  SELECT 'Corte clásico' nombre, 'Corte con tijera y máquina, acabado con peinado.' descripcion, 'CORTES' categoria, 12000 precio_centavos
  UNION ALL SELECT 'Corte degradé (fade)', 'Degradé profesional con acabado a navaja.', 'CORTES', 15000
  UNION ALL SELECT 'Corte infantil', 'Corte para menores de 12 años, con paciencia incluida.', 'CORTES', 9000
  UNION ALL SELECT 'Barba completa', 'Perfilado de barba con toalla caliente y bálsamo.', 'BARBA', 8000
  UNION ALL SELECT 'Barba y bigote', 'Recorte y perfilado de barba con bigote.', 'BARBA', 7000
  UNION ALL SELECT 'Diseño de barba', 'Figuras y líneas marcadas a navaja.', 'BARBA', 10000
  UNION ALL SELECT 'Combo corte + barba', 'Corte completo más perfilado de barba.', 'COMBOS', 18000
  UNION ALL SELECT 'Combo completo', 'Corte, barba y cejas en una sola sesión.', 'COMBOS', 21000
  UNION ALL SELECT 'Cejas', 'Perfilado de cejas con navaja.', 'CEJAS', 4000
  UNION ALL SELECT 'Delineado de cejas', 'Delineado fino de cejas a navaja.', 'CEJAS', 5000
  UNION ALL SELECT 'Tinte para barba', 'Cobertura de canas en barba, tono natural.', 'COLOR', 13000
  UNION ALL SELECT 'Tinte completo', 'Tinte de cabello con lavado incluido.', 'COLOR', 25000
) AS datos
JOIN categorias c ON c.nombre = datos.categoria
WHERE NOT EXISTS (
  SELECT 1 FROM servicios s WHERE s.categoria_id = c.id AND s.nombre = datos.nombre
);

INSERT INTO barberos (nombre, anios_experiencia, activo)
SELECT datos.nombre, datos.anios_experiencia, 1
FROM (
  SELECT 'Carlos Mendoza' nombre, 8 anios_experiencia
  UNION ALL SELECT 'Luis Ramírez', 6
  UNION ALL SELECT 'Andrea Sosa', 10
  UNION ALL SELECT 'Jorge Vidal', 12
) datos
WHERE NOT EXISTS (SELECT 1 FROM barberos b WHERE b.nombre = datos.nombre);

INSERT IGNORE INTO especialidades (nombre, activo) VALUES
  ('Cortes clásicos', 1),
  ('Degradés y fades', 1),
  ('Barba y navaja', 1),
  ('Colorimetría', 1),
  ('Peinados', 1),
  ('Cejas', 1);

INSERT IGNORE INTO barbero_especialidad (barbero_id, especialidad_id)
SELECT b.id, e.id
FROM (
  SELECT 'Carlos Mendoza' barbero, 'Cortes clásicos' especialidad
  UNION ALL SELECT 'Carlos Mendoza', 'Degradés y fades'
  UNION ALL SELECT 'Luis Ramírez', 'Barba y navaja'
  UNION ALL SELECT 'Andrea Sosa', 'Colorimetría'
  UNION ALL SELECT 'Jorge Vidal', 'Peinados'
  UNION ALL SELECT 'Jorge Vidal', 'Cortes clásicos'
) x
JOIN barberos b ON b.nombre = x.barbero
JOIN especialidades e ON e.nombre = x.especialidad;

-- Horarios semanales (2=lunes … 7=sábado; domingo cerrado).
INSERT IGNORE INTO barbero_horarios (barbero_id, dia_semana, hora_inicio, hora_fin, duracion_turno_minutos)
SELECT b.id, d.dia_semana, h.inicio, h.fin, 30
FROM (
  SELECT 'Carlos Mendoza' nombre, '09:00:00' inicio, '18:00:00' fin
  UNION ALL SELECT 'Luis Ramírez', '10:00:00', '20:00:00'
  UNION ALL SELECT 'Andrea Sosa', '11:00:00', '19:00:00'
  UNION ALL SELECT 'Jorge Vidal', '09:00:00', '17:00:00'
) h
JOIN barberos b ON b.nombre = h.nombre
JOIN (SELECT 2 dia_semana UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d;

-- ============================================================
-- Consultas útiles
-- ============================================================
-- Servicios activos:
-- SELECT s.*, c.nombre AS categoria
-- FROM servicios s JOIN categorias c ON c.id = s.categoria_id
-- WHERE s.activo = 1 ORDER BY s.nombre;
--
-- Barberos activos con especialidades:
-- SELECT b.nombre, GROUP_CONCAT(e.nombre SEPARATOR ' · ') AS especialidades
-- FROM barberos b
-- LEFT JOIN barbero_especialidad be ON be.barbero_id = b.id
-- LEFT JOIN especialidades e ON e.id = be.especialidad_id AND e.activo = 1
-- WHERE b.activo = 1 GROUP BY b.id ORDER BY b.nombre;
--
-- Historial de servicios completados por cliente (RF08):
-- SELECT c.id AS cita_id, c.fecha, c.hora_inicio, c.nombre_servicio,
--        c.precio_centavos, b.nombre AS barbero
-- FROM citas c JOIN barberos b ON b.id = c.barbero_id
-- WHERE c.cliente_id = ? AND c.estado = 'COMPLETADA'
-- ORDER BY c.fecha DESC, c.hora_inicio DESC;
--
-- Precio mostrado al usuario en pesos mexicanos:
-- SELECT nombre, precio_centavos / 100.0 AS precio_mxn FROM servicios;
