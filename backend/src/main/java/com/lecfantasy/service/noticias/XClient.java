package com.lecfantasy.service.noticias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecfantasy.config.AppProperties;
import com.lecfantasy.domain.OrigenNoticia;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publicaciones de las cuentas oficiales en X (Twitter) con la API v2.
 * La API de X es de pago por uso (se paga cada publicación leída), así que:
 * - se excluyen respuestas y reposts,
 * - solo se piden las publicaciones posteriores a la última importada (since_id),
 * - y como mucho {@code maxPorConsulta} por cuenta en cada ejecución.
 * El token (X_BEARER_TOKEN) se queda en el servidor.
 */
@Component
public class XClient {

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final AppProperties.X config;
    private final Map<String, String> idsCuenta = new ConcurrentHashMap<>();

    public XClient(ObjectMapper mapper, AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.rest = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.config = props.noticias().x();
    }

    public boolean configurado() {
        return config.token() != null && !config.token().isBlank();
    }

    public List<String> cuentas() {
        return config.cuentas() == null ? List.of()
                : config.cuentas().stream().map(c -> c.strip().replaceFirst("^@", "")).filter(c -> !c.isEmpty()).toList();
    }

    /** Publicaciones de una cuenta (sin @) posteriores a {@code desdeId}, o las últimas si es null. */
    public List<NoticiaExterna> publicaciones(String cuenta, String desdeId) {
        String id = idsCuenta.computeIfAbsent(cuenta.toLowerCase(), c -> idDeCuenta(cuenta));
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(config.url()).path("/2/users/{id}/tweets")
                .queryParam("max_results", Math.max(5, Math.min(100, config.maxPorConsulta())))
                .queryParam("exclude", "replies,retweets")
                .queryParam("tweet.fields", "created_at,attachments,entities,note_tweet")
                .queryParam("expansions", "attachments.media_keys")
                .queryParam("media.fields", "url,preview_image_url,type");
        if (desdeId != null) {
            b.queryParam("since_id", desdeId);
        }
        return convertir(pedir(b.encode().buildAndExpand(id).toUri()), cuenta);
    }

    private String idDeCuenta(String cuenta) {
        URI uri = UriComponentsBuilder.fromUriString(config.url()).path("/2/users/by/username/{u}")
                .encode().buildAndExpand(cuenta).toUri();
        String id = pedir(uri).path("data").path("id").asText("");
        if (id.isEmpty()) {
            throw new IllegalStateException("X no encuentra la cuenta @" + cuenta);
        }
        return id;
    }

    private JsonNode pedir(URI uri) {
        String cuerpo = rest.get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.token())
                .retrieve()
                .body(String.class);
        try {
            return mapper.readTree(cuerpo == null ? "{}" : cuerpo);
        } catch (Exception e) {
            throw new IllegalStateException("Respuesta de X no válida", e);
        }
    }

    /** Convierte la respuesta de GET /2/users/{id}/tweets. Público para los tests. */
    public static List<NoticiaExterna> convertir(JsonNode respuesta, String cuenta) {
        Map<String, String> imagenes = new HashMap<>();
        for (JsonNode m : respuesta.path("includes").path("media")) {
            String url = m.path("url").asText(m.path("preview_image_url").asText(""));
            if (!url.isEmpty()) {
                imagenes.put(m.path("media_key").asText(), url);
            }
        }
        List<NoticiaExterna> lista = new ArrayList<>();
        for (JsonNode t : respuesta.path("data")) {
            String id = t.path("id").asText();
            String texto = limpiar(t.path("note_tweet").path("text").asText(t.path("text").asText("")), t.path("entities"));
            if (id.isEmpty() || texto.isBlank()) {
                continue;
            }
            String imagen = null;
            for (JsonNode k : t.path("attachments").path("media_keys")) {
                imagen = imagenes.get(k.asText());
                if (imagen != null) {
                    break;
                }
            }
            Instant fecha = t.hasNonNull("created_at") ? Instant.parse(t.get("created_at").asText()) : Instant.now();
            lista.add(new NoticiaExterna(OrigenNoticia.X, id, "@" + cuenta, NoticiaExterna.tituloDe(texto),
                    NoticiaExterna.recortar(texto, 10_000), imagen, "https://x.com/" + cuenta + "/status/" + id, fecha));
        }
        return lista;
    }

    /**
     * Texto legible: los enlaces acortados t.co pasan a su dirección real, los de fotos y vídeos
     * (que ya se ven como imagen) se quitan, y se deshacen las entidades HTML que manda X.
     */
    static String limpiar(String texto, JsonNode entidades) {
        String t = texto;
        for (JsonNode u : entidades.path("urls")) {
            String corto = u.path("url").asText("");
            String largo = u.path("expanded_url").asText("");
            if (corto.isEmpty()) {
                continue;
            }
            boolean multimedia = u.hasNonNull("media_key") || largo.contains("/photo/") || largo.contains("/video/");
            t = t.replace(corto, multimedia ? "" : largo);
        }
        t = t.replaceAll("https://t\\.co/\\w+", "");
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        return t.strip();
    }
}
