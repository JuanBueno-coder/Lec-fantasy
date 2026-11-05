package com.lecfantasy.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura de las respuestas de Cargo e imageinfo de Leaguepedia, sin red. */
class LeaguepediaFotosClientTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void leeLosArchivosDeCargo() throws Exception {
        JsonNode filas = om.readTree("""
                [
                  {"title": {"Pagina": "Caps", "Imagen": "G2 Caps 2026 Split 1.png"}},
                  {"title": {"Pagina": "Jun (Yoon Se-jun)", "Imagen": "G2 Jun 2026.png"}},
                  {"title": {"Pagina": "SinFoto", "Imagen": ""}}
                ]""");
        Map<String, String> archivos = LeaguepediaFotosClient.leerArchivos(filas, "Pagina");
        assertThat(archivos).containsEntry("caps", "G2 Caps 2026 Split 1.png")
                .containsEntry("jun (yoon se-jun)", "G2 Jun 2026.png")
                .doesNotContainKey("sinfoto");
    }

    @Test
    void leeLasMiniaturasDeImageinfoYDeshaceLaNormalizacion() throws Exception {
        JsonNode raiz = om.readTree("""
                {"batchcomplete": true, "query": {
                  "normalized": [{"fromencoded": false, "from": "File:G2_Caps_2026.png", "to": "File:G2 Caps 2026.png"}],
                  "pages": [
                    {"ns": 6, "title": "File:G2 Caps 2026.png", "imageinfo": [{
                      "thumburl": "https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/a/ab/G2_Caps_2026.png/revision/latest/scale-to-width-down/256",
                      "url": "https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/a/ab/G2_Caps_2026.png"}]},
                    {"ns": 6, "title": "File:No existe.png", "missing": true}
                  ]}}""");
        Map<String, String> urls = LeaguepediaFotosClient.leerUrls(raiz, List.of("G2_Caps_2026.png", "No existe.png"));
        assertThat(urls).hasSize(1);
        assertThat(urls.get("G2_Caps_2026.png")).contains("scale-to-width-down/256");
    }

    @Test
    void soloDescargaDelCdnDeFandomPorHttps() {
        assertThat(LeaguepediaFotosClient.hostPermitido(URI.create("https://static.wikia.nocookie.net/a.png"))).isTrue();
        assertThat(LeaguepediaFotosClient.hostPermitido(URI.create("http://static.wikia.nocookie.net/a.png"))).isFalse();
        assertThat(LeaguepediaFotosClient.hostPermitido(URI.create("https://ejemplo.com/a.png"))).isFalse();
        assertThat(LeaguepediaFotosClient.hostPermitido(URI.create("https://nocookie.net.ejemplo.com/a.png"))).isFalse();
    }

    @Test
    void escapaLosNombresDelWhere() {
        assertThat(LeaguepediaFotosClient.lista(List.of("Caps", "Raro\"Nombre\\")))
                .isEqualTo("\"Caps\",\"RaroNombre\"");
    }
}
