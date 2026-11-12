-- =====================================================================
-- V6 · Noticias de canales oficiales (X / Twitter y YouTube de la LEC)
-- Las noticias pueden venir de un administrador (MANUAL) o importarse solas
-- de una cuenta oficial. id_externo evita duplicar la misma publicación.
-- =====================================================================
ALTER TABLE noticia ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE noticia ADD COLUMN id_externo VARCHAR(100);
ALTER TABLE noticia ADD COLUMN url_externa VARCHAR(500);
ALTER TABLE noticia ADD COLUMN autor_externo VARCHAR(100);
ALTER TABLE noticia ADD CONSTRAINT uk_noticia_externa UNIQUE (origen, id_externo);
CREATE INDEX idx_noticia_fecha ON noticia (publicada, fecha_publicacion);
