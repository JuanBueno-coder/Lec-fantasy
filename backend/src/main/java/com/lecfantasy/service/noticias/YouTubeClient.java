package com.lecfantasy.service.noticias;

import com.lecfantasy.config.AppProperties;
import com.lecfantasy.domain.OrigenNoticia;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vídeos de los canales oficiales de YouTube a través de su feed RSS público
 * (https://www.youtube.com/feeds/videos.xml?channel_id=...): gratis y sin key. Da los últimos 15 vídeos.
 * El canal se configura por su id (UC...) o por su @usuario, que se resuelve al id una vez.
 */
@Component
public class YouTubeClient {

    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String YT = "http://www.youtube.com/xml/schemas/2015";
    private static final String MEDIA = "http://search.yahoo.com/mrss/";
    private static final Pattern ID_CANAL = Pattern.compile("^UC[\\w-]{22}$");
    private static final List<Pattern> ID_EN_PAGINA = List.of(
            Pattern.compile("<link rel=\"canonical\" href=\"https://www\\.youtube\\.com/channel/(UC[\\w-]{22})\""),
            Pattern.compile("\"externalId\":\"(UC[\\w-]{22})\""),
            Pattern.compile("\"channelId\":\"(UC[\\w-]{22})\""));

    private final RestClient rest;
    private final AppProperties.YouTube config;
    private final Map<String, String> idsCanal = new ConcurrentHashMap<>();

    public YouTubeClient(AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.rest = RestClient.builder().requestFactory(factory).build();
        this.config = props.noticias().youtube();
    }

    public List<String> canales() {
        return config.canales() == null ? List.of()
                : config.canales().stream().map(String::strip).filter(c -> !c.isEmpty()).toList();
    }

    /** Últimos vídeos de un canal (id UC... o @usuario). */
    public List<NoticiaExterna> videos(String canal) {
        String id = idsCanal.computeIfAbsent(canal, this::resolver);
        URI uri = UriComponentsBuilder.fromUriString("https://www.youtube.com/feeds/videos.xml")
                .queryParam("channel_id", id).encode().build().toUri();
        return convertir(pedir(uri), canal.startsWith("@") ? canal : null);
    }

    private String resolver(String canal) {
        if (ID_CANAL.matcher(canal).matches()) {
            return canal;
        }
        String usuario = canal.startsWith("@") ? canal : "@" + canal;
        String pagina = pedir(URI.create("https://www.youtube.com/" + usuario));
        for (Pattern p : ID_EN_PAGINA) {
            Matcher m = p.matcher(pagina);
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new IllegalStateException("No se encuentra el id del canal de YouTube " + usuario
                + ". Pon el id (UC...) en YOUTUBE_CANALES.");
    }

    private String pedir(URI uri) {
        return rest.get().uri(uri)
                // Evita la página de consentimiento de cookies que YouTube enseña en Europa
                .header(HttpHeaders.COOKIE, "SOCS=CAI; CONSENT=YES+1")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "es-ES,es;q=0.9,en;q=0.8")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; LECFantasyTFG/1.0)")
                .retrieve()
                .body(String.class);
    }

    /** Convierte el feed Atom de un canal. Público para los tests. */
    public static List<NoticiaExterna> convertir(String xml, String usuario) {
        Document doc = leer(xml);
        String autor = usuario;
        if (autor == null) {
            Element nombre = primero(doc.getDocumentElement(), ATOM, "name");
            autor = nombre == null ? "YouTube" : nombre.getTextContent().strip();
        }
        List<NoticiaExterna> lista = new ArrayList<>();
        NodeList entradas = doc.getElementsByTagNameNS(ATOM, "entry");
        for (int i = 0; i < entradas.getLength(); i++) {
            Element e = (Element) entradas.item(i);
            String id = texto(e, YT, "videoId");
            String titulo = texto(e, ATOM, "title");
            if (id == null || titulo == null) {
                continue;
            }
            String descripcion = texto(e, MEDIA, "description");
            Element miniatura = primero(e, MEDIA, "thumbnail");
            String imagen = miniatura == null ? "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg"
                    : miniatura.getAttribute("url");
            String publicado = texto(e, ATOM, "published");
            Instant fecha = publicado == null ? Instant.now() : OffsetDateTime.parse(publicado).toInstant();
            String contenido = descripcion == null || descripcion.isBlank() ? titulo : descripcion.strip();
            lista.add(new NoticiaExterna(OrigenNoticia.YOUTUBE, id, autor, NoticiaExterna.recortar(titulo.strip(), 200),
                    NoticiaExterna.recortar(contenido, 10_000), imagen, "https://www.youtube.com/watch?v=" + id, fecha));
        }
        return lista;
    }

    private static Document leer(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            // Sin DTD ni entidades externas (XXE)
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("El feed de YouTube no es válido", e);
        }
    }

    private static Element primero(Element padre, String ns, String nombre) {
        NodeList l = padre.getElementsByTagNameNS(ns, nombre);
        for (int i = 0; i < l.getLength(); i++) {
            Node n = l.item(i);
            // Solo hijos directos o dentro de media:group, nunca de otra entrada
            if (n instanceof Element el && (n.getParentNode() == padre || n.getParentNode().getParentNode() == padre)) {
                return el;
            }
        }
        return null;
    }

    private static String texto(Element padre, String ns, String nombre) {
        Element e = primero(padre, ns, nombre);
        return e == null ? null : e.getTextContent();
    }
}
