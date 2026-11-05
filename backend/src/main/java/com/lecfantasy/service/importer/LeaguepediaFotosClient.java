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
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fotos de los jugadores desde Leaguepedia, en tres pasos:
 * <ol>
 *   <li><b>Cargo</b>, tabla {@code Players}: el campo {@code Image} es el archivo de la foto principal de la
 *       página del jugador (la misma que se ve en su ficha de la wiki). Si el nombre no es una página de
 *       jugador, se busca en {@code PlayerRedirects} (nombres antiguos y alias).</li>
 *   <li><b>imageinfo</b> (API de MediaWiki): convierte el archivo en la URL de una miniatura del ancho pedido.</li>
 *   <li><b>Descarga</b> de la miniatura desde el CDN de Fandom (static.wikia.nocookie.net).</li>
 * </ol>
 * Las peticiones a la API van de 50 en 50 y con pausa, porque Leaguepedia limita las peticiones por minuto.
 */
@Component
public class LeaguepediaFotosClient {

    private static final Logger log = LoggerFactory.getLogger(LeaguepediaFotosClient.class);
    static final int LOTE = 50;
    private static final int MAX_BYTES = 2_000_000;

    /** Imagen descargada. */
    public record Foto(byte[] contenido, String tipo) {
    }

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final AppProperties.Leaguepedia props;

    public LeaguepediaFotosClient(ObjectMapper mapper, AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.rest = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.props = props.leaguepedia();
    }

    // ------------------------- 1. Cargo: archivo de la foto -------------------------

    /**
     * @param paginas páginas de jugador de Leaguepedia ("Caps", "Jun (Yoon Se-jun)") o nicknames
     * @return nombre en minúsculas → archivo de su foto (p. ej. "G2 Caps 2026 Split 1.png"). Solo los encontrados.
     */
    public Map<String, String> archivos(Collection<String> paginas) {
        Map<String, String> resultado = new HashMap<>();
        for (List<String> lote : lotes(paginas)) {
            JsonNode filas = cargo(Map.of(
                    "tables", "Players=P",
                    "fields", "P.OverviewPage=Pagina,P.Image=Imagen",
                    "where", "P.OverviewPage IN (" + lista(lote) + ")"));
            resultado.putAll(leerArchivos(filas, "Pagina"));
            pausa();
        }

        // Los que no son una página de jugador: alias o nombre antiguo (PlayerRedirects)
        List<String> restantes = paginas.stream().distinct()
                .filter(p -> !resultado.containsKey(clave(p))).toList();
        if (!restantes.isEmpty()) {
            try {
                for (List<String> lote : lotes(restantes)) {
                    JsonNode filas = cargo(Map.of(
                            "tables", "PlayerRedirects=PR,Players=P",
                            "join_on", "PR.OverviewPage=P.OverviewPage",
                            "fields", "PR.AllName=Nombre,P.Image=Imagen",
                            "where", "PR.AllName IN (" + lista(lote) + ")"));
                    leerArchivos(filas, "Nombre").forEach(resultado::putIfAbsent);
                    pausa();
                }
            } catch (ApiException e) {
                // Es solo un respaldo: si falla, nos quedamos con lo encontrado en Players
                log.warn("Leaguepedia: no se pudieron buscar alias de jugadores ({})", e.getMessage());
            }
        }
        log.info("Leaguepedia: {} de {} jugadores con foto en la wiki", resultado.size(), paginas.size());
        return resultado;
    }

    /** Filas de cargoquery → nombre en minúsculas → archivo (se ignoran los que no tienen imagen). */
    static Map<String, String> leerArchivos(JsonNode filas, String campoNombre) {
        Map<String, String> mapa = new HashMap<>();
        for (JsonNode fila : filas) {
            JsonNode t = fila.path("title");
            String nombre = t.path(campoNombre).asText("").trim();
            String imagen = t.path("Imagen").asText("").trim();
            if (!nombre.isEmpty() && !imagen.isEmpty()) {
                mapa.putIfAbsent(clave(nombre), imagen);
            }
        }
        return mapa;
    }

    private JsonNode cargo(Map<String, String> parametros) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(props.url())
                .queryParam("action", "cargoquery")
                .queryParam("format", "json")
                .queryParam("limit", 500);
        Map<String, String> valores = new LinkedHashMap<>();
        parametros.forEach((k, v) -> {
            b.queryParam(k, "{" + k + "}");
            valores.put(k, v);
        });
        JsonNode raiz = pedirJson(b.encode().buildAndExpand(valores).toUri());
        return raiz.path("cargoquery");
    }

    // ------------------------- 2. imageinfo: URL de la miniatura -------------------------

    /**
     * @param archivos nombres de archivo sin el prefijo "File:"
     * @param ancho    ancho de la miniatura en píxeles
     * @return archivo (tal como se pidió) → URL de la miniatura. Solo los que existen.
     */
    public Map<String, String> urls(Collection<String> archivos, int ancho) {
        Map<String, String> resultado = new HashMap<>();
        for (List<String> lote : lotes(archivos)) {
            String titulos = lote.stream().map(a -> "File:" + a).collect(Collectors.joining("|"));
            URI uri = UriComponentsBuilder.fromUriString(props.url())
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("formatversion", 2)
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url")
                    .queryParam("iiurlwidth", ancho)
                    .queryParam("titles", "{titulos}")
                    .encode()
                    .buildAndExpand(Map.of("titulos", titulos))
                    .toUri();
            resultado.putAll(leerUrls(pedirJson(uri), lote));
            pausa();
        }
        return resultado;
    }

    /**
     * Respuesta de imageinfo → archivo pedido → URL. MediaWiki normaliza los títulos
     * ("File:g2_caps.png" → "File:G2 caps.png"), así que se deshace con la lista "normalized".
     */
    static Map<String, String> leerUrls(JsonNode raiz, Collection<String> pedidos) {
        Map<String, String> normalizado = new HashMap<>();
        for (JsonNode n : raiz.path("query").path("normalized")) {
            normalizado.put(n.path("from").asText(), n.path("to").asText());
        }
        Map<String, String> porTitulo = new HashMap<>();
        for (JsonNode pagina : paginas(raiz.path("query").path("pages"))) {
            JsonNode info = pagina.path("imageinfo").path(0);
            String url = info.path("thumburl").asText(info.path("url").asText(""));
            if (!pagina.has("missing") && !url.isBlank()) {
                porTitulo.put(pagina.path("title").asText(), url);
            }
        }
        Map<String, String> resultado = new HashMap<>();
        for (String archivo : pedidos) {
            String titulo = "File:" + archivo;
            String url = porTitulo.get(normalizado.getOrDefault(titulo, titulo));
            if (url != null) {
                resultado.put(archivo, url);
            }
        }
        return resultado;
    }

    /** "pages" es una lista con formatversion=2 y un objeto con formatversion=1. */
    private static Iterable<JsonNode> paginas(JsonNode pages) {
        if (pages.isArray()) {
            return pages;
        }
        List<JsonNode> lista = new ArrayList<>();
        pages.elements().forEachRemaining(lista::add);
        return lista;
    }

    // ------------------------- 3. Descarga -------------------------

    public Foto descargar(String url) {
        URI uri = URI.create(url);
        if (!hostPermitido(uri)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Host de imagen no permitido: " + uri.getHost());
        }
        try {
            ResponseEntity<byte[]> r = rest.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    // El CDN de Fandom rechaza a veces las peticiones sin Referer de la wiki
                    .header(HttpHeaders.REFERER, "https://lol.fandom.com/")
                    .accept(MediaType.parseMediaType("image/webp"), MediaType.parseMediaType("image/*"))
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] cuerpo = r.getBody();
            MediaType tipo = r.getHeaders().getContentType();
            if (cuerpo == null || cuerpo.length == 0 || tipo == null || !"image".equals(tipo.getType())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "La respuesta no es una imagen");
            }
            if (cuerpo.length > MAX_BYTES) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Imagen demasiado grande");
            }
            return new Foto(cuerpo, tipo.getType() + "/" + tipo.getSubtype());
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo descargar la imagen: " + e.getMessage());
        }
    }

    static boolean hostPermitido(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return "https".equals(uri.getScheme())
                && (host.endsWith(".nocookie.net") || host.equals("lol.fandom.com"));
    }

    // ------------------------- Utilidades -------------------------

    private JsonNode pedirJson(URI uri) {
        JsonNode raiz;
        try {
            String cuerpo = rest.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .retrieve()
                    .body(String.class);
            raiz = mapper.readTree(cuerpo == null ? "{}" : cuerpo);
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con Leaguepedia: " + e.getMessage());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Respuesta de Leaguepedia no válida");
        }
        if (raiz.has("error")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Leaguepedia devolvió un error: " + raiz.path("error").path("info").asText());
        }
        return raiz;
    }

    static String clave(String nombre) {
        return nombre.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /** "a", "b" → {@code "a","b"} para un IN de Cargo (sin comillas ni barras dentro). */
    static String lista(Collection<String> valores) {
        return valores.stream()
                .map(v -> "\"" + v.replace("\"", "").replace("\\", "") + "\"")
                .collect(Collectors.joining(","));
    }

    private static List<List<String>> lotes(Collection<String> valores) {
        List<String> unicos = valores.stream().filter(Objects::nonNull).distinct().toList();
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < unicos.size(); i += LOTE) {
            lotes.add(unicos.subList(i, Math.min(i + LOTE, unicos.size())));
        }
        return lotes;
    }

    private static void pausa() {
        try {
            Thread.sleep(1_500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
