package com.lecfantasy.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecfantasy.service.importer.LeaguepediaFotosClient;
import com.lecfantasy.service.importer.LeaguepediaFotosClient.Foto;
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
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * Fotos de Leaguepedia (cliente simulado): se descargan, se guardan en la base de datos, se sirven en
 * /api/jugadores/{id}/foto, no se vuelven a bajar si no cambian y el administrador puede quitarlas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url="
        + "jdbc:h2:mem:fotos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1")
class FotosTest {

    private static final String URL_CAPS = "https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/caps.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockitoBean
    private LeaguepediaFotosClient leaguepedia;

    @Test
    void traeGuardaYSirveLasFotos() throws Exception {
        String admin = enviar(post("/api/auth/login"), null,
                Map.of("identificador", "admin@test.com", "password", "admin12345"), 200).get("token").asText();
        long caps = crearJugador(admin, "Caps", "MID");
        long sinFoto = crearJugador(admin, "Desconocido", "TOP");

        when(leaguepedia.archivos(any())).thenReturn(Map.of("caps", "G2 Caps 2026.png"));
        when(leaguepedia.urls(any(), anyInt())).thenReturn(Map.of("G2 Caps 2026.png", URL_CAPS));
        when(leaguepedia.descargar(URL_CAPS)).thenReturn(new Foto(PNG, "image/png"));

        JsonNode r = enviar(post("/api/admin/jugadores/fotos/leaguepedia"), admin, null, 200);
        assertThat(r.get("actualizadas").asInt()).isEqualTo(1);
        assertThat(r.get("sinFoto").toString()).contains("Desconocido");

        // El jugador lleva la ruta de la foto, versionada
        String url = enviar(get("/api/jugadores/" + caps), null, null, 200).get("jugador").get("fotoUrl").asText();
        assertThat(url).startsWith("/api/jugadores/" + caps + "/foto?v=");

        // Se sirve sin iniciar sesión, con su tipo y en caché
        MvcResult foto = mvc.perform(get(url)).andReturn();
        assertThat(foto.getResponse().getStatus()).isEqualTo(200);
        assertThat(foto.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(foto.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(foto.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("immutable");
        enviar(get("/api/jugadores/" + sinFoto + "/foto"), null, null, 404);

        // Sin "todas" solo se miran los que no tienen foto; con "todas" no se vuelve a bajar la misma
        r = enviar(post("/api/admin/jugadores/fotos/leaguepedia?todas=true"), admin, null, 200);
        assertThat(r.get("actualizadas").asInt()).isZero();
        assertThat(r.get("sinCambios").asInt()).isEqualTo(1);
        verify(leaguepedia, times(1)).descargar(URL_CAPS);

        // Editar el jugador sin tocar la foto la conserva
        enviar(put("/api/admin/jugadores/" + caps), admin, jugador("Caps", "MID", url), 200);
        assertThat(enviar(get("/api/jugadores/" + caps), null, null, 200).get("jugador").get("fotoUrl").asText())
                .isEqualTo(url);

        // Si el administrador la borra, se quita también la descargada
        enviar(put("/api/admin/jugadores/" + caps), admin, jugador("Caps", "MID", ""), 200);
        assertThat(enviar(get("/api/jugadores/" + caps), null, null, 200).get("jugador").get("fotoUrl").isNull())
                .isTrue();
        enviar(get("/api/jugadores/" + caps + "/foto"), null, null, 404);
    }

    private long crearJugador(String admin, String nickname, String posicion) throws Exception {
        return enviar(post("/api/admin/jugadores"), admin, jugador(nickname, posicion, null), 201).get("id").asLong();
    }

    private static Map<String, Object> jugador(String nickname, String posicion, String fotoUrl) {
        Map<String, Object> m = new HashMap<>();
        m.put("nickname", nickname);
        m.put("posicion", posicion);
        m.put("valorMercado", 8_000_000);
        m.put("fotoUrl", fotoUrl);
        return m;
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
