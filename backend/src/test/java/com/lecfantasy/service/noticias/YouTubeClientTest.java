package com.lecfantasy.service.noticias;

import com.lecfantasy.domain.OrigenNoticia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Conversión del feed RSS (Atom) de un canal de YouTube a noticias. */
class YouTubeClientTest {

    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/"
                  xmlns="http://www.w3.org/2005/Atom">
              <title>LEC</title>
              <author><name>LEC</name><uri>https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv</uri></author>
              <entry>
                <id>yt:video:abc123</id>
                <yt:videoId>abc123</yt:videoId>
                <title>G2 vs FNC | Highlights</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=abc123"/>
                <author><name>LEC</name></author>
                <published>2026-11-10T19:00:00+00:00</published>
                <media:group>
                  <media:title>G2 vs FNC | Highlights</media:title>
                  <media:thumbnail url="https://i2.ytimg.com/vi/abc123/hqdefault.jpg" width="480" height="360"/>
                  <media:description>Lo mejor del partido</media:description>
                </media:group>
              </entry>
              <entry>
                <yt:videoId>def456</yt:videoId>
                <title>Resumen de la semana</title>
                <published>2026-11-09T19:00:00+00:00</published>
                <media:group><media:description></media:description></media:group>
              </entry>
            </feed>
            """;

    @Test
    void convierteLosVideos() {
        List<NoticiaExterna> lista = YouTubeClient.convertir(FEED, "@LEC");

        assertThat(lista).hasSize(2);
        NoticiaExterna v = lista.get(0);
        assertThat(v.origen()).isEqualTo(OrigenNoticia.YOUTUBE);
        assertThat(v.idExterno()).isEqualTo("abc123");
        assertThat(v.cuenta()).isEqualTo("@LEC");
        assertThat(v.titulo()).isEqualTo("G2 vs FNC | Highlights");
        assertThat(v.contenido()).isEqualTo("Lo mejor del partido");
        assertThat(v.imagenUrl()).isEqualTo("https://i2.ytimg.com/vi/abc123/hqdefault.jpg");
        assertThat(v.url()).isEqualTo("https://www.youtube.com/watch?v=abc123");
        assertThat(v.fecha()).isEqualTo(Instant.parse("2026-11-10T19:00:00Z"));

        // Sin descripción ni miniatura: el título como contenido y la miniatura estándar del vídeo
        assertThat(lista.get(1).contenido()).isEqualTo("Resumen de la semana");
        assertThat(lista.get(1).imagenUrl()).isEqualTo("https://i.ytimg.com/vi/def456/hqdefault.jpg");
    }

    @Test
    void sinUsuarioUsaElNombreDelCanal() {
        assertThat(YouTubeClient.convertir(FEED, null).get(0).cuenta()).isEqualTo("LEC");
    }

    @Test
    void rechazaDocumentosConDtd() {
        String malicioso = "<?xml version=\"1.0\"?><!DOCTYPE feed [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title>&x;</title></feed>";
        assertThatThrownBy(() -> YouTubeClient.convertir(malicioso, "@LEC")).isInstanceOf(IllegalStateException.class);
    }
}
