package com.lecfantasy.service.noticias;

import com.lecfantasy.config.AppProperties;
import com.lecfantasy.domain.Noticia;
import com.lecfantasy.domain.OrigenNoticia;
import com.lecfantasy.domain.TipoActividad;
import com.lecfantasy.repository.NoticiaRepository;
import com.lecfantasy.security.AuthUser;
import com.lecfantasy.service.ActividadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Trae las noticias de los canales oficiales de la LEC y las guarda como noticias publicadas:
 * - X (Twitter): las cuentas de X_CUENTAS, si hay X_BEARER_TOKEN (de pago por uso).
 * - YouTube: los canales de YOUTUBE_CANALES por su feed RSS (gratis).
 * No duplica nada (origen + id de la publicación) y un canal que falle no frena a los demás.
 */
@Service
public class NoticiasExternasService {

    private static final Logger log = LoggerFactory.getLogger(NoticiasExternasService.class);

    private final NoticiaRepository noticias;
    private final XClient x;
    private final YouTubeClient youtube;
    private final ActividadService actividad;
    private final AppProperties.Noticias config;
    private volatile Importacion ultima;

    public NoticiasExternasService(NoticiaRepository noticias, XClient x, YouTubeClient youtube,
                                   ActividadService actividad, AppProperties props) {
        this.noticias = noticias;
        this.x = x;
        this.youtube = youtube;
        this.actividad = actividad;
        this.config = props.noticias();
    }

    /** Resultado de un canal: cuántas noticias nuevas trajo o por qué falló. */
    public record Canal(String origen, String cuenta, int nuevas, String error) {
    }

    public record Importacion(LocalDateTime fecha, boolean manual, int nuevas, List<Canal> canales) {
    }

    /** Lo que muestra el panel de administración. */
    public record Estado(boolean automaticas, String cron, boolean x, List<String> cuentasX,
                         List<String> canalesYoutube, Importacion ultima) {
    }

    public Estado estado() {
        return new Estado(config.automaticas(), config.cron(), x.configurado(), x.cuentas(), youtube.canales(), ultima);
    }

    @Scheduled(cron = "${app.noticias.cron}")
    public void tarea() {
        if (config.automaticas()) {
            importar(null);
        }
    }

    /** Al arrancar (p. ej. tras un despliegue) se importa una vez en segundo plano, sin esperar al cron. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        if (config.automaticas()) {
            Thread hilo = new Thread(() -> {
                try {
                    Importacion r = importar(null);
                    log.info("Noticias: {} nuevas al arrancar ({})", r.nuevas(), r.canales());
                } catch (Exception e) {
                    log.warn("Noticias: la importación al arrancar falló: {}", e.getMessage());
                }
            }, "noticias-arranque");
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    /** Importa ahora. admin = botón del panel; null = tarea programada. */
    public synchronized Importacion importar(AuthUser admin) {
        List<Canal> canales = new ArrayList<>();
        if (x.configurado()) {
            for (String cuenta : x.cuentas()) {
                canales.add(importarCanal(OrigenNoticia.X, "@" + cuenta, () -> {
                    String desde = noticias.findFirstByOrigenAndAutorExternoOrderByFechaPublicacionDesc(
                            OrigenNoticia.X, "@" + cuenta).map(Noticia::getIdExterno).orElse(null);
                    return x.publicaciones(cuenta, desde);
                }));
            }
        }
        for (String canal : youtube.canales()) {
            canales.add(importarCanal(OrigenNoticia.YOUTUBE, canal, () -> youtube.videos(canal)));
        }
        int nuevas = canales.stream().mapToInt(Canal::nuevas).sum();
        Importacion resultado = new Importacion(LocalDateTime.now(), admin != null, nuevas, canales);
        ultima = resultado;
        if (canales.stream().anyMatch(c -> c.error() == null)) {
            log.info("Noticias: {} nuevas ({})", nuevas, canales);
        }
        if (admin != null || nuevas > 0) {
            String quien = admin == null ? "El sistema (automático)" : admin.nombre();
            actividad.registrar(admin, TipoActividad.NOTICIA,
                    quien + " importó " + nuevas + (nuevas == 1 ? " noticia" : " noticias") + " de los canales oficiales");
        }
        return resultado;
    }

    private interface Lector {
        List<NoticiaExterna> leer();
    }

    private Canal importarCanal(OrigenNoticia origen, String cuenta, Lector lector) {
        try {
            int nuevas = 0;
            for (NoticiaExterna e : lector.leer()) {
                if (guardar(e)) {
                    nuevas++;
                }
            }
            return new Canal(origen.name(), cuenta, nuevas, null);
        } catch (Exception ex) {
            log.warn("Noticias: no se pudo leer {} {}: {}", origen, cuenta, ex.getMessage());
            return new Canal(origen.name(), cuenta, 0, mensaje(ex));
        }
    }

    /** Guarda una publicación si no estaba ya. */
    boolean guardar(NoticiaExterna e) {
        if (noticias.existsByOrigenAndIdExterno(e.origen(), e.idExterno())) {
            return false;
        }
        Noticia n = new Noticia();
        n.setOrigen(e.origen());
        n.setIdExterno(e.idExterno());
        n.setAutorExterno(NoticiaExterna.recortar(e.cuenta(), 100));
        n.setTitulo(NoticiaExterna.recortar(e.titulo(), 200));
        n.setContenido(e.contenido());
        n.setImagenUrl(e.imagenUrl() == null || e.imagenUrl().length() > 500 ? null : e.imagenUrl());
        n.setUrlExterna(NoticiaExterna.recortar(e.url(), 500));
        n.setPublicada(true);
        // Fechas en UTC sin zona, como el resto de la API (la web las convierte a la hora local)
        n.setFechaPublicacion(LocalDateTime.ofInstant(e.fecha(), ZoneOffset.UTC));
        noticias.save(n);
        return true;
    }

    private static String mensaje(Exception ex) {
        String m = ex.getMessage();
        if (m == null) {
            return ex.getClass().getSimpleName();
        }
        return m.length() > 200 ? m.substring(0, 200) + "…" : m;
    }
}
