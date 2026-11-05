package com.lecfantasy.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lecfantasy.common.ApiException;
import com.lecfantasy.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todas las llamadas a la API de Leaguepedia (lol.fandom.com/api.php) pasan por aquí.
 * <p>
 * Fandom limita mucho a los clientes sin cuenta: en la práctica <b>una consulta cada 30-60 s</b>, y si se
 * insiste estando limitado la penalización se alarga. Por eso:
 * <ul>
 *   <li>Deja un intervalo mínimo entre consultas (40 s sin cuenta, 4 s con cuenta).</li>
 *   <li>Si Leaguepedia responde "ratelimited", espera 1, 2 y 4 minutos antes de reintentar.</li>
 *   <li>Si se configuran {@code LEAGUEPEDIA_USUARIO} y {@code LEAGUEPEDIA_PASSWORD} (un <i>bot password</i>
 *       creado en lol.fandom.com/wiki/Special:BotPasswords), inicia sesión y los límites son mucho mayores.</li>
 * </ul>
 * Los métodos son {@code synchronized}: nunca hay dos consultas a la vez, aunque las pidan la importación
 * y las fotos al mismo tiempo.
 */
@Component
public class LeaguepediaApi {

    private static final Logger log = LoggerFactory.getLogger(LeaguepediaApi.class);
    static final long[] ESPERAS_REINTENTO_MS = {60_000, 120_000, 240_000};
    private static final Duration DURACION_SESION = Duration.ofHours(1);

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final AppProperties.Leaguepedia props;
    private final long intervaloAnonimoMs;
    private final long intervaloConCuentaMs;

    private long ultimaPeticion;
    private long sesionIniciada;
    private boolean conSesion;

    public LeaguepediaApi(ObjectMapper mapper, AppProperties props) {
        // HttpClient de Java con cookies: la sesión de Leaguepedia va en una cookie
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.rest = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.props = props.leaguepedia();
        this.intervaloAnonimoMs = 40_000;
        this.intervaloConCuentaMs = 4_000;
    }

    /** true si hay usuario y contraseña de bot configurados. */
    public boolean tieneCuenta() {
        return noVacio(props.usuario()) && noVacio(props.password());
    }

    /**
     * Hace una petición GET a api.php con estos parámetros (además de format=json).
     * Espera lo necesario para no pasarse del límite y reintenta si Leaguepedia lo pide.
     */
    public synchronized JsonNode consultar(Map<String, String> parametros) {
        asegurarSesion();
        for (int intento = 0; ; intento++) {
            esperarTurno();
            JsonNode raiz = get(parametros);
            if (!esLimite(raiz)) {
                if (raiz.has("error")) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY,
                            "Leaguepedia devolvió un error: " + raiz.path("error").path("info").asText());
                }
                return raiz;
            }
            if (intento >= ESPERAS_REINTENTO_MS.length) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, mensajeLimite());
            }
            long espera = ESPERAS_REINTENTO_MS[intento];
            log.warn("Leaguepedia: límite de peticiones alcanzado; reintento {} dentro de {} s",
                    intento + 1, espera / 1000);
            dormir(espera);
        }
    }

    String mensajeLimite() {
        return "Leaguepedia está limitando las consultas" + (conSesion ? "" : " (sin cuenta solo deja una por minuto)")
                + ". Vuelve a intentarlo dentro de unos minutos"
                + (conSesion ? "." : " o configura LEAGUEPEDIA_USUARIO y LEAGUEPEDIA_PASSWORD (bot password).");
    }

    static boolean esLimite(JsonNode raiz) {
        return "ratelimited".equals(raiz.path("error").path("code").asText());
    }

    // ------------------------- Peticiones -------------------------

    private JsonNode get(Map<String, String> parametros) {
        Map<String, String> todos = new LinkedHashMap<>(parametros);
        todos.put("format", "json");
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(props.url());
        todos.keySet().forEach(k -> b.queryParam(k, "{" + k + "}"));
        URI uri = b.encode().buildAndExpand(todos).toUri();
        try {
            String cuerpo = rest.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .retrieve()
                    .body(String.class);
            return leer(cuerpo);
        } catch (HttpClientErrorException.TooManyRequests e) {
            return mapper.createObjectNode().set("error", mapper.createObjectNode().put("code", "ratelimited"));
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Leaguepedia: " + e.getMessage());
        }
    }

    private JsonNode post(MultiValueMap<String, String> formulario) {
        try {
            String cuerpo = rest.post().uri(props.url())
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(String.class);
            return leer(cuerpo);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Leaguepedia: " + e.getMessage());
        }
    }

    private JsonNode leer(String cuerpo) {
        try {
            return mapper.readTree(cuerpo == null ? "{}" : cuerpo);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Respuesta de Leaguepedia no válida");
        }
    }

    // ------------------------- Sesión (bot password) -------------------------

    private void asegurarSesion() {
        if (!tieneCuenta() || (conSesion && System.currentTimeMillis() - sesionIniciada < DURACION_SESION.toMillis())) {
            return;
        }
        try {
            esperarTurno();
            String token = get(Map.of("action", "query", "meta", "tokens", "type", "login"))
                    .path("query").path("tokens").path("logintoken").asText("");
            MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
            f.add("action", "login");
            f.add("format", "json");
            f.add("lgname", props.usuario());
            f.add("lgpassword", props.password());
            f.add("lgtoken", token);
            esperarTurno();
            JsonNode r = post(f).path("login");
            conSesion = "Success".equals(r.path("result").asText());
            sesionIniciada = System.currentTimeMillis();
            if (conSesion) {
                log.info("Leaguepedia: sesión iniciada como {}", r.path("lgusername").asText(props.usuario()));
            } else {
                log.warn("Leaguepedia: no se pudo iniciar sesión ({}); se sigue sin cuenta", r.path("reason").asText(
                        r.path("result").asText("respuesta desconocida")));
            }
        } catch (ApiException e) {
            conSesion = false;
            sesionIniciada = System.currentTimeMillis();
            log.warn("Leaguepedia: no se pudo iniciar sesión ({}); se sigue sin cuenta", e.getMessage());
        }
    }

    // ------------------------- Ritmo -------------------------

    private void esperarTurno() {
        long intervalo = conSesion ? intervaloConCuentaMs : intervaloAnonimoMs;
        long espera = ultimaPeticion + intervalo - System.currentTimeMillis();
        if (ultimaPeticion > 0 && espera > 0) {
            dormir(espera);
        }
        ultimaPeticion = System.currentTimeMillis();
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta a Leaguepedia interrumpida");
        }
    }

    private static boolean noVacio(String s) {
        return s != null && !s.isBlank();
    }
}
