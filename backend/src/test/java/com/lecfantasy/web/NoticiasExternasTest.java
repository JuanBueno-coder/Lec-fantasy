package com.lecfantasy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecfantasy.domain.OrigenNoticia;
import com.lecfantasy.service.noticias.NoticiaExterna;
import com.lecfantasy.service.noticias.XClient;
import com.lecfantasy.service.noticias.YouTubeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Noticias de los canales oficiales (X y YouTube simulados): se importan sin duplicarse,
 * X solo pide las publicaciones nuevas y un canal caído no frena a los demás.
 * También comprueba «La semana en la LEC» sin jornadas jugadas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url="
        + "jdbc:h2:mem:noticias;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
class NoticiasExternasTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockitoBean
    private XClient x;

    @MockitoBean
    private YouTubeClient youtube;

    @Test
    void importaNoticiasDeLosCanalesOficiales() throws Exception {
        String admin = enviar(post("/api/auth/login"), null,
                Map.of("identificador", "admin@test.com", "password", "admin12345"), 200).get("token").asText();

        when(x.configurado()).thenReturn(true);
        when(x.cuentas()).thenReturn(List.of("LEC"));
        when(x.publicaciones(eq("LEC"), any())).thenReturn(List.of(
                new NoticiaExterna(OrigenNoticia.X, "1002", "@LEC", "¡G2 campeón!", "¡G2 campeón! Así fue la final",
                        "https://pbs.twimg.com/media/final.jpg", "https://x.com/LEC/status/1002",
                        Instant.parse("2026-11-10T18:00:00Z")),
                new NoticiaExterna(OrigenNoticia.X, "1001", "@LEC", "Mañana, la final", "Mañana, la final", null,
                        "https://x.com/LEC/status/1001", Instant.parse("2026-11-09T18:00:00Z"))));
        when(youtube.canales()).thenReturn(List.of("@LEC"));
        when(youtube.videos("@LEC")).thenReturn(List.of(
                new NoticiaExterna(OrigenNoticia.YOUTUBE, "abc123", "@LEC", "Highlights de la final", "Lo mejor",
                        "https://i.ytimg.com/vi/abc123/hqdefault.jpg", "https://www.youtube.com/watch?v=abc123",
                        Instant.parse("2026-11-10T20:00:00Z"))));

        JsonNode fuentes = enviar(get("/api/admin/noticias/fuentes"), admin, null, 200);
        assertThat(fuentes.get("x").asBoolean()).isTrue();
        assertThat(fuentes.get("cuentasX").get(0).asText()).isEqualTo("LEC");

        JsonNode primera = enviar(post("/api/admin/noticias/importar"), admin, null, 200);
        assertThat(primera.get("nuevas").asInt()).isEqualTo(3);
        assertThat(primera.get("canales")).hasSize(2);
        verify(x).publicaciones(eq("LEC"), isNull());

        // Públicas, de la más reciente a la más antigua, con el enlace a la publicación original
        JsonNode lista = enviar(get("/api/noticias?tamano=10"), null, null, 200);
        assertThat(lista.get("totalElementos").asInt()).isEqualTo(3);
        JsonNode video = lista.get("contenido").get(0);
        assertThat(video.get("origen").asText()).isEqualTo("YOUTUBE");
        assertThat(video.get("urlExterna").asText()).isEqualTo("https://www.youtube.com/watch?v=abc123");
        JsonNode tuit = lista.get("contenido").get(1);
        assertThat(tuit.get("origen").asText()).isEqualTo("X");
        assertThat(tuit.get("autor").asText()).isEqualTo("@LEC");
        assertThat(tuit.get("imagenUrl").asText()).isEqualTo("https://pbs.twimg.com/media/final.jpg");

        // Segunda vez: a X solo se le piden las posteriores a la última importada y no se duplica nada
        JsonNode segunda = enviar(post("/api/admin/noticias/importar"), admin, null, 200);
        assertThat(segunda.get("nuevas").asInt()).isZero();
        verify(x).publicaciones("LEC", "1002");
        assertThat(enviar(get("/api/noticias"), null, null, 200).get("totalElementos").asInt()).isEqualTo(3);

        // Si YouTube falla, X sigue funcionando y el error queda en el resultado
        when(youtube.videos("@LEC")).thenThrow(new IllegalStateException("YouTube no responde"));
        JsonNode conFallo = enviar(post("/api/admin/noticias/importar"), admin, null, 200);
        JsonNode canalYoutube = conFallo.get("canales").get(1);
        assertThat(canalYoutube.get("origen").asText()).isEqualTo("YOUTUBE");
        assertThat(canalYoutube.get("error").asText()).contains("YouTube no responde");
        assertThat(conFallo.get("canales").get(0).get("error").isNull()).isTrue();

        // Solo los administradores pueden lanzar la importación
        enviar(post("/api/admin/noticias/importar"), null, null, 401);
    }

    @Test
    void laSemanaSinJornadasJugadas() throws Exception {
        JsonNode semana = enviar(get("/api/semana"), null, null, 200);
        assertThat(semana.get("jornada").isNull()).isTrue();
        assertThat(semana.get("top")).isEmpty();
        assertThat(semana.get("suben")).isEmpty();
        assertThat(semana.get("bajan")).isEmpty();
    }

    private JsonNode enviar(MockHttpServletRequestBuilder peticion, String token, Object cuerpo, int estadoEsperado)
            throws Exception {
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(cuerpo));
        }
        MvcResult r = mvc.perform(peticion).andReturn();
        String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus())
                .as("%s %s → %s", r.getRequest().getMethod(), r.getRequest().getRequestURI(), body)
                .isEqualTo(estadoEsperado);
        return body.isBlank() ? om.createObjectNode() : om.readTree(body);
    }
}
