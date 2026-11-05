package com.lecfantasy.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fotos de los jugadores desde Leaguepedia, en tres pasos:
 * <ol>
 *   <li><b>Cargo</b>, tabla {@code PlayerImages} (una foto por jugador y torneo) cruzada con {@code Tournaments}
 *       para quedarse con la del torneo más reciente. ({@code Players.Image} está vacío en Leaguepedia y
 *       {@code PlayerImages.SortDate} también.) Si el nombre no es una página de jugador, se busca por sus
 *       alias en {@code PlayerRedirects}.</li>
 *   <li><b>URL de la miniatura</b>: se calcula como lo hace MediaWiki (carpetas por el MD5 del nombre del
 *       archivo), sin gastar consultas. Si esa URL falla, se pregunta a la API con {@code imageinfo}.</li>
 *   <li><b>Descarga</b> de la miniatura desde el CDN de Fandom (static.wikia.nocookie.net), que no cuenta para
 *       el límite de la API.</li>
 * </ol>
 * Leaguepedia limita mucho las consultas (ver {@link LeaguepediaApi}), así que se piden todos los jugadores en
 * una sola consulta Cargo siempre que se puede.
 */
@Component
public class LeaguepediaFotosClient {

    private static final Logger log = LoggerFactory.getLogger(LeaguepediaFotosClient.class);
    /** Nombres por consulta Cargo (cada jugador tiene una foto por torneo: unas 10-30 filas). */
    static final int LOTE_CARGO = 100;
    private static final int FILAS_POR_PAGINA = 500;
    /** De la foto más reciente a la más antigua (SortDate viene vacío; se usa la fecha del torneo). */
    private static final String ORDEN_RECIENTE = "T.DateStart DESC,PI._ID DESC";
    /** Títulos por consulta imageinfo: el máximo de MediaWiki para clientes sin permisos de bot. */
    static final int LOTE = 50;
    /** Carpeta de imágenes de Leaguepedia en el CDN de Fandom. */
    static final String CDN = "https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/";
    private static final int MAX_BYTES = 2_000_000;

    /** Imagen descargada. */
    public record Foto(byte[] contenido, String tipo) {
    }

    private final RestClient rest;
    private final LeaguepediaApi api;
    private final AppProperties.Leaguepedia props;

    public LeaguepediaFotosClient(LeaguepediaApi api, AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.rest = RestClient.builder().requestFactory(factory).build();
        this.api = api;
        this.props = props.leaguepedia();
    }

    // ------------------------- 1. Cargo: archivo de la foto -------------------------

    /**
     * @param paginas páginas de jugador de Leaguepedia ("Caps", "Jun (Yoon Se-jun)") o nicknames
     * @return nombre en minúsculas → archivo de su foto (p. ej. "G2 Caps 2026 Split 1.png"). Solo los encontrados.
     */
    public Map<String, String> archivos(Collection<String> paginas) {
        Map<String, String> resultado = new HashMap<>();
        for (List<String> lote : lotes(paginas, LOTE_CARGO)) {
            JsonNode filas = cargo(Map.of(
                    "tables", "PlayerImages=PI,Tournaments=T",
                    "join_on", "PI.Tournament=T.OverviewPage",
                    "fields", "PI.Link=Pagina,PI.FileName=Imagen",
                    "where", "PI.Link IN (" + lista(lote) + ")",
                    "order_by", ORDEN_RECIENTE));
            resultado.putAll(leerArchivos(filas, "Pagina"));
        }

        // Los que no son una página de jugador: alias o nombre antiguo (PlayerRedirects)
        List<String> restantes = paginas.stream().distinct()
                .filter(p -> !resultado.containsKey(clave(p))).toList();
        if (!restantes.isEmpty()) {
            try {
                for (List<String> lote : lotes(restantes, LOTE_CARGO)) {
                    JsonNode filas = cargo(Map.of(
                            "tables", "PlayerRedirects=PR,PlayerImages=PI,Tournaments=T",
                            "join_on", "PR.OverviewPage=PI.Link,PI.Tournament=T.OverviewPage",
                            "fields", "PR.AllName=Nombre,PI.FileName=Imagen",
                            "where", "PR.AllName IN (" + lista(lote) + ") AND PI.FileName IS NOT NULL",
                            "order_by", ORDEN_RECIENTE));
                    leerArchivos(filas, "Nombre").forEach(resultado::putIfAbsent);
                }
            } catch (ApiException e) {
                if (e.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) {
                    throw e; // límite de Leaguepedia: que lo vea el administrador
                }
                // Es solo un respaldo: si falla, nos quedamos con lo encontrado
                log.warn("Leaguepedia: no se pudieron buscar alias de jugadores ({})", e.getMessage());
            }
        }
        log.info("Leaguepedia: {} de {} jugadores con foto en la wiki", resultado.size(), paginas.size());
        return resultado;
    }

    /**
     * Filas de cargoquery → nombre en minúsculas → archivo (se ignoran los que no tienen imagen).
     * Vienen ordenadas de la foto más reciente a la más antigua, así que se queda la primera de cada jugador.
     */
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

    /** Todas las filas de una consulta Cargo, pidiendo páginas de 500 (como mucho 10 páginas). */
    private JsonNode cargo(Map<String, String> parametros) {
        ArrayNode todas = JsonNodeFactory.instance.arrayNode();
        for (int pagina = 0; pagina < 10; pagina++) {
            Map<String, String> todos = new LinkedHashMap<>(parametros);
            todos.put("action", "cargoquery");
            todos.put("limit", String.valueOf(FILAS_POR_PAGINA));
            todos.put("offset", String.valueOf(pagina * FILAS_POR_PAGINA));
            JsonNode filas = api.consultar(todos).path("cargoquery");
            filas.forEach(todas::add);
            if (filas.size() < FILAS_POR_PAGINA) {
                break;
            }
        }
        return todas;
    }

    // ------------------------- 2. URL de la miniatura -------------------------

    /**
     * URL de la miniatura calculada sin consultar la API, como la guarda MediaWiki:
     * {@code images/<md5[0]>/<md5[0..1]>/<Nombre_del_archivo>} (espacios como "_" y primera letra en mayúscula)
     * más el sufijo de Fandom para redimensionar.
     */
    public String urlMiniatura(String archivo, int ancho) {
        return urlDirecta(archivo, ancho);
    }

    static String urlDirecta(String archivo, int ancho) {
        String nombre = archivo.trim().replace(' ', '_');
        nombre = nombre.substring(0, 1).toUpperCase(Locale.ROOT) + nombre.substring(1);
        String md5 = md5(nombre);
        return CDN + md5.charAt(0) + "/" + md5.substring(0, 2) + "/"
                + UriUtils.encodePathSegment(nombre, StandardCharsets.UTF_8)
                + "/revision/latest/scale-to-width-down/" + ancho;
    }

    private static String md5(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Respaldo: pregunta a la API la URL de cada archivo (imageinfo). Gasta una consulta por cada 50.
     *
     * @param archivos nombres de archivo sin el prefijo "File:"
     * @param ancho    ancho de la miniatura en píxeles
     * @return archivo (tal como se pidió) → URL de la miniatura. Solo los que existen.
     */
    public Map<String, String> urls(Collection<String> archivos, int ancho) {
        Map<String, String> resultado = new HashMap<>();
        for (List<String> lote : lotes(archivos, LOTE)) {
            String titulos = lote.stream().map(a -> "File:" + a).collect(Collectors.joining("|"));
            JsonNode raiz = api.consultar(Map.of(
                    "action", "query",
                    "formatversion", "2",
                    "prop", "imageinfo",
                    "iiprop", "url",
                    "iiurlwidth", String.valueOf(ancho),
                    "titles", titulos));
            resultado.putAll(leerUrls(raiz, lote));
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

    static String clave(String nombre) {
        return nombre.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    /** "a", "b" → {@code "a","b"} para un IN de Cargo (sin comillas ni barras dentro). */
    static String lista(Collection<String> valores) {
        return valores.stream()
                .map(v -> "\"" + v.replace("\"", "").replace("\\", "") + "\"")
                .collect(Collectors.joining(","));
    }

    private static List<List<String>> lotes(Collection<String> valores, int tam) {
        List<String> unicos = valores.stream().filter(Objects::nonNull).distinct().toList();
        List<List<String>> lotes = new ArrayList<>();
        for (int i = 0; i < unicos.size(); i += tam) {
            lotes.add(unicos.subList(i, Math.min(i + tam, unicos.size())));
        }
        return lotes;
    }

}
