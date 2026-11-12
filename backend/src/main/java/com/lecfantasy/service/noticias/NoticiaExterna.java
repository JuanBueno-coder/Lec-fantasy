package com.lecfantasy.service.noticias;

import com.lecfantasy.domain.OrigenNoticia;

import java.time.Instant;

/**
 * Una publicación de un canal oficial lista para guardarse como noticia.
 * cuenta: quién la publicó tal y como se muestra (p. ej. "@LEC").
 */
public record NoticiaExterna(OrigenNoticia origen, String idExterno, String cuenta, String titulo, String contenido,
                             String imagenUrl, String url, Instant fecha) {

    private static final int MAX_TITULO = 140;

    /** Título a partir de un texto largo: la primera línea, cortada por una palabra si no cabe. */
    static String tituloDe(String texto) {
        String linea = texto.strip().lines().filter(l -> !l.isBlank()).findFirst().orElse("").strip();
        if (linea.length() <= MAX_TITULO) {
            return linea;
        }
        int corte = linea.lastIndexOf(' ', MAX_TITULO - 1);
        return linea.substring(0, corte > 60 ? corte : MAX_TITULO - 1).strip() + "…";
    }

    /** Recorta al tamaño de una columna. */
    static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
