-- =====================================================================
-- V4 · Mercado de mejoras con inventario y mejoras configurables
-- SQL compatible con MySQL 8 y con H2 en modo MySQL (tests).
--
-- Reglas:
--   * Las mejoras se compran en el mercado de mejoras y van al INVENTARIO del equipo
--     (cada equipo es de una liga, así que cada liga tiene su propio inventario).
--   * Desde el inventario se asignan a un TITULAR para la próxima jornada:
--     cada titular (TOP, JNG, MID, ADC, SUP) puede llevar como mucho una mejora.
--   * Cada mejora tiene un solo uso: vale para todas las partidas de esa jornada
--     y se evalúa partida a partida. Al cerrar la jornada se gasta.
-- =====================================================================

-- ---------- MEJORA: condición y efecto configurables ----------
-- Multiplicador con decimales (×1,25, ×1,5...)
ALTER TABLE mejora MODIFY COLUMN multiplicador_bono DECIMAL(5,2) NOT NULL;
-- Condición de tipo ESTADISTICA: "<estadistica> <comparador> <umbral>", p. ej. MUERTES MENOR 3
ALTER TABLE mejora ADD COLUMN estadistica VARCHAR(20);
ALTER TABLE mejora ADD COLUMN comparador VARCHAR(15);
-- Efecto: MULTIPLICADOR (puntos de la partida × multiplicador) o
-- PUNTOS_POR_ACCION (cambia lo que vale cada unidad de una estadística, p. ej. muerte −0,5)
ALTER TABLE mejora ADD COLUMN efecto VARCHAR(20) NOT NULL DEFAULT 'MULTIPLICADOR';
ALTER TABLE mejora ADD COLUMN efecto_estadistica VARCHAR(20);
ALTER TABLE mejora ADD COLUMN puntos_por_unidad DECIMAL(6,2);
-- Cuántas puede tener un equipo a la vez (en inventario + asignadas). NULL = sin límite.
ALTER TABLE mejora ADD COLUMN max_por_equipo INT;

-- ---------- MEJORA_APLICADA: ahora es un objeto del inventario ----------
-- Sin jugador ni jornada mientras está en el inventario.
ALTER TABLE mejora_aplicada MODIFY COLUMN id_jugador_real BIGINT NULL;
ALTER TABLE mejora_aplicada MODIFY COLUMN id_jornada BIGINT NULL;
ALTER TABLE mejora_aplicada ADD COLUMN fecha_compra DATETIME(6);

-- ---------- Condiciones antiguas pasadas al formato nuevo ----------
UPDATE mejora SET condicion = 'ESTADISTICA', estadistica = 'MUERTES', comparador = 'IGUAL', umbral = 0,
                  nombre = 'Intocable', descripcion = 'No muere en la partida'
WHERE condicion = 'CERO_MUERTES';
UPDATE mejora SET condicion = 'ESTADISTICA', estadistica = 'KDA', comparador = 'MAYOR_IGUAL',
                  descripcion = 'KDA de la partida igual o superior al umbral'
WHERE condicion = 'KDA_MINIMO';
UPDATE mejora SET condicion = 'VICTORIA', descripcion = 'Su equipo gana la partida'
WHERE condicion = 'VICTORIAS';
UPDATE mejora SET descripcion = 'Consigue al menos una triple kill en la partida' WHERE condicion = 'TRIPLE_KILL';
UPDATE mejora SET descripcion = 'Consigue al menos una quadra kill en la partida' WHERE condicion = 'QUADRA_KILL';
UPDATE mejora SET descripcion = 'Consigue una pentakill en la partida' WHERE condicion = 'PENTA_KILL';
UPDATE mejora SET descripcion = 'Participa en la primera sangre de la partida' WHERE condicion = 'FIRST_BLOOD';
UPDATE mejora SET descripcion = 'Es el MVP de la partida' WHERE condicion = 'MVP';

-- ---------- Mejoras nuevas ----------
INSERT INTO mejora (nombre, descripcion, precio, multiplicador_bono, condicion, estadistica, comparador, umbral,
                    efecto, efecto_estadistica, puntos_por_unidad, max_por_equipo, activa) VALUES
('Capitán',    'Gratis, una por equipo: sus puntos valen ×1,25 en todas sus partidas',
               0.00,       1.25, 'SIEMPRE',             NULL,      NULL,    NULL, 'MULTIPLICADOR',     NULL,      NULL, 1,    TRUE),
('Inmortal',   'Muere menos de 3 veces en la partida',
               1500000.00, 1.50, 'ESTADISTICA',         'MUERTES', 'MENOR', 3,    'MULTIPLICADOR',     NULL,      NULL, NULL, TRUE),
('Inventor',   'Juega un campeón poco visto: elegido 3 veces o menos en la LEC antes de esa partida',
               2000000.00, 2.00, 'CAMPEON_POCO_JUGADO', NULL,      NULL,    3,    'MULTIPLICADOR',     NULL,      NULL, NULL, TRUE),
('Morador',    'Muere menos de 5 veces en la partida',
               800000.00,  1.25, 'ESTADISTICA',         'MUERTES', 'MENOR', 5,    'MULTIPLICADOR',     NULL,      NULL, NULL, TRUE),
('Good death', 'Cada muerte resta −0,5 puntos en lugar de −1',
               600000.00,  1.00, 'SIEMPRE',             NULL,      NULL,    NULL, 'PUNTOS_POR_ACCION', 'MUERTES', -0.50, NULL, TRUE);
