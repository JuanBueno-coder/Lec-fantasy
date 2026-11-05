-- =====================================================================
-- V5 · Fotos de los jugadores traídas de Leaguepedia (API Cargo)
-- SQL compatible con MySQL 8 y con H2 en modo MySQL (tests).
--
-- La imagen se descarga una vez y se guarda aquí, y la API la sirve en
-- GET /api/jugadores/{id}/foto. Así la web y la app Android usan la misma URL
-- y no dependen de que Fandom permita enlazar sus imágenes desde otras webs.
-- =====================================================================

-- Marca de la última foto descargada (milisegundos). Va en la URL (?v=) para que
-- el navegador vuelva a pedirla cuando cambia. NULL = sin foto descargada.
ALTER TABLE jugador_real ADD COLUMN foto_version BIGINT;

CREATE TABLE foto_jugador (
    id_jugador_real BIGINT       NOT NULL PRIMARY KEY,
    contenido       MEDIUMBLOB   NOT NULL,
    tipo_contenido  VARCHAR(60)  NOT NULL,
    -- Nombre del archivo en Leaguepedia, para no volver a descargar la misma foto
    archivo_origen  VARCHAR(255),
    actualizada     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_foto_jugador FOREIGN KEY (id_jugador_real)
        REFERENCES jugador_real (id_jugador_real) ON DELETE CASCADE
);
