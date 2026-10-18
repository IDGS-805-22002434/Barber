-- ============================================================
-- BarberManager - Esquema MySQL
-- Equivalente server-side de la base local Room de la app.
-- - Los precios se guardan en CENTAVOS (12000 = $120.00),
--   igual que la columna priceCents de la app.
-- - Especialidades N:N con barberos (RF03.03).
-- Importar con: mysql -u root -p < db/barbermanager.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS barbermanager
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE barbermanager;

-- ------------------------------------------------------------
-- Categorías de servicios (enum de la app: CORTES, BARBA, ...)
-- ------------------------------------------------------------
CREATE TABLE categorias (
  id    BIGINT       NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(50) NOT NULL,
  icono VARCHAR(50)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categoria_nombre (nombre)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Servicios del catálogo
-- ------------------------------------------------------------
CREATE TABLE servicios (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  nombre          VARCHAR(100) NOT NULL,
  descripcion     VARCHAR(255) NOT NULL DEFAULT '',
  categoria_id    BIGINT       NOT NULL,
  precio_centavos BIGINT       NOT NULL,
  activo          TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT fk_servicios_categoria
    FOREIGN KEY (categoria_id) REFERENCES categorias (id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT chk_servicios_precio CHECK (precio_centavos >= 0),
  KEY ix_servicios_categoria (categoria_id),
  KEY ix_servicios_activo (activo)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Barberos del equipo (RF03)
-- ------------------------------------------------------------
CREATE TABLE barberos (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  nombre             VARCHAR(100) NOT NULL,
  anios_experiencia  INT          NOT NULL DEFAULT 0,
  activo             TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  CONSTRAINT chk_barberos_experiencia CHECK (anios_experiencia >= 0),
  KEY ix_barberos_activo (activo)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Catálogo de especialidades (RF03.03)
-- ------------------------------------------------------------
CREATE TABLE especialidades (
  id     BIGINT       NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  activo TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_especialidad_nombre (nombre)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- Relación N:N barbero ↔ especialidad (RF03.03)
-- ------------------------------------------------------------
CREATE TABLE barbero_especialidad (
  barbero_id      BIGINT NOT NULL,
  especialidad_id BIGINT NOT NULL,
  PRIMARY KEY (barbero_id, especialidad_id),
  CONSTRAINT fk_be_barbero
    FOREIGN KEY (barbero_id) REFERENCES barberos (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_be_especialidad
    FOREIGN KEY (especialidad_id) REFERENCES especialidades (id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  KEY ix_be_especialidad (especialidad_id)
) ENGINE=InnoDB;

-- ============================================================
-- Datos iniciales (mismas semillas que la app)
-- ============================================================

INSERT INTO categorias (nombre, icono) VALUES
  ('CORTES', 'content_cut'),
  ('BARBA',  'man'),
  ('COMBOS', 'style'),
  ('CEJAS',  'face'),
  ('COLOR',  'palette');

-- Categorías por posición: 1=CORTES, 2=BARBA, 3=COMBOS, 4=CEJAS, 5=COLOR
INSERT INTO servicios (nombre, descripcion, categoria_id, precio_centavos, activo) VALUES
  ('Corte clásico',          'Corte con tijera y máquina, acabado con peinado.',   1, 12000, 1),
  ('Corte degradé (fade)',   'Degradé profesional con acabado a navaja.',          1, 15000, 1),
  ('Corte infantil',         'Corte para menores de 12 años, con paciencia incluida.', 1, 9000, 1),
  ('Barba completa',         'Perfilado de barba con toalla caliente y bálsamo.',  2,  8000, 1),
  ('Barba y bigote',         'Recorte y perfilado de barba con bigote.',           2,  7000, 1),
  ('Diseño de barba',        'Figuras y líneas marcadas a navaja.',                2, 10000, 1),
  ('Combo corte + barba',    'Corte completo más perfilado de barba.',             3, 18000, 1),
  ('Combo completo',         'Corte, barba y cejas en una sola sesión.',           3, 21000, 1),
  ('Cejas',                  'Perfilado de cejas con navaja.',                     4,  4000, 1),
  ('Delineado de cejas',     'Delineado fino de cejas a navaja.',                  4,  5000, 1),
  ('Tinte para barba',       'Cobertura de canas en barba, tono natural.',         5, 13000, 1),
  ('Tinte completo',         'Tinte de cabello con lavado incluido.',              5, 25000, 1);

INSERT INTO barberos (nombre, anios_experiencia, activo) VALUES
  ('Carlos Mendoza',  8, 1),
  ('Luis Ramírez',    6, 1),
  ('Andrea Sosa',    10, 1),
  ('Jorge Vidal',    12, 1);

-- Especialidades por posición: 1=Cortes clásicos, 2=Degradés y fades,
-- 3=Barba y navaja, 4=Colorimetría, 5=Peinados, 6=Cejas
INSERT INTO especialidades (nombre, activo) VALUES
  ('Cortes clásicos', 1),
  ('Degradés y fades', 1),
  ('Barba y navaja', 1),
  ('Colorimetría', 1),
  ('Peinados', 1),
  ('Cejas', 1);

-- Barberos por posición: 1=Carlos, 2=Luis, 3=Andrea, 4=Jorge
INSERT INTO barbero_especialidad (barbero_id, especialidad_id) VALUES
  (1, 1), (1, 2),
  (2, 3),
  (3, 4),
  (4, 5), (4, 1);

-- ============================================================
-- Consultas útiles
-- ============================================================
-- Servicios activos ordenados por nombre (lo que ve el cliente):
--   SELECT s.*, c.nombre AS categoria
--   FROM servicios s JOIN categorias c ON c.id = s.categoria_id
--   WHERE s.activo = 1 ORDER BY s.nombre;
--
-- Barberos con sus especialidades (RF03.01 / RF03.03):
--   SELECT b.nombre, GROUP_CONCAT(e.nombre SEPARATOR ' · ') AS especialidades
--   FROM barberos b
--   LEFT JOIN barbero_especialidad be ON be.barbero_id = b.id
--   LEFT JOIN especialidades e ON e.id = be.especialidad_id AND e.activo = 1
--   WHERE b.activo = 1
--   GROUP BY b.id ORDER BY b.nombre;
--
-- Precio como decimal: SELECT nombre, precio_centavos / 100.0 AS precio FROM servicios;
