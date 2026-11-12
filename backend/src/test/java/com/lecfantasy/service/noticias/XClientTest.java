package com.lecfantasy.service.noticias;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecfantasy.domain.OrigenNoticia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Conversión de la respuesta de la API v2 de X (GET /2/users/{id}/tweets) a noticias. */
class XClientTest {

    private static final String RESPUESTA = """
            {"data":[
              {"id":"1002","text":"¡G2 gana la final! https://t.co/abc https://t.co/foto",
               "created_at":"2026-11-10T18:00:00.000Z",
               "attachments":{"media_keys":["3_1"]},
               "entities":{"urls":[
                 {"url":"https://t.co/abc","expanded_url":"https://lolesports.com/news/final"},
                 {"url":"https://t.co/foto","expanded_url":"https://x.com/LEC/status/1002/photo/1","media_key":"3_1"}]}},
              {"id":"1001","text":"Fish &amp; chips &lt;3","created_at":"2026-11-09T18:00:00.000Z"},
              {"id":"1000","text":"https://t.co/solofoto","created_at":"2026-11-08T18:00:00.000Z"}],
             "includes":{"media":[{"media_key":"3_1","type":"photo","url":"https://pbs.twimg.com/media/final.jpg"}]},
             "meta":{"result_count":3,"newest_id":"1002"}}
            """;

    @Test
    void convierteLasPublicaciones() throws Exception {
        List<NoticiaExterna> lista = XClient.convertir(new ObjectMapper().readTree(RESPUESTA), "LEC");

        // La que solo tenía un enlace a una foto se descarta: no tiene texto
        assertThat(lista).hasSize(2);
        NoticiaExterna final_ = lista.get(0);
        assertThat(final_.origen()).isEqualTo(OrigenNoticia.X);
        assertThat(final_.idExterno()).isEqualTo("1002");
        assertThat(final_.cuenta()).isEqualTo("@LEC");
        assertThat(final_.contenido()).isEqualTo("¡G2 gana la final! https://lolesports.com/news/final");
        assertThat(final_.titulo()).isEqualTo(final_.contenido());
        assertThat(final_.imagenUrl()).isEqualTo("https://pbs.twimg.com/media/final.jpg");
        assertThat(final_.url()).isEqualTo("https://x.com/LEC/status/1002");
        assertThat(final_.fecha()).isEqualTo(Instant.parse("2026-11-10T18:00:00Z"));

        assertThat(lista.get(1).contenido()).isEqualTo("Fish & chips <3");
        assertThat(lista.get(1).imagenUrl()).isNull();
    }

    @Test
    void respuestaSinPublicacionesNuevas() throws Exception {
        assertThat(XClient.convertir(new ObjectMapper().readTree("{\"meta\":{\"result_count\":0}}"), "LEC")).isEmpty();
    }

    @Test
    void elTituloEsLaPrimeraLineaRecortada() {
        assertThat(NoticiaExterna.tituloDe("Primera línea\n\nSegunda")).isEqualTo("Primera línea");
        String largo = "palabra ".repeat(40).strip();
        String titulo = NoticiaExterna.tituloDe(largo);
        assertThat(titulo).endsWith("…").hasSizeLessThanOrEqualTo(140);
        assertThat(titulo).doesNotContain("palabr…");
    }
}
