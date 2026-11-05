package com.lecfantasy.service;

import com.lecfantasy.common.ApiException;
import com.lecfantasy.domain.FotoJugador;
import com.lecfantasy.domain.JugadorReal;
import com.lecfantasy.domain.TipoActividad;
import com.lecfantasy.repository.FotoJugadorRepository;
import com.lecfantasy.repository.JugadorRealRepository;
import com.lecfantasy.security.AuthUser;
import com.lecfantasy.service.importer.LeaguepediaFotosClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Trae las fotos de los jugadores de Leaguepedia y las guarda en la base de datos.
 * Las llamadas a Leaguepedia se hacen fuera de transacción; cada foto se guarda en la suya.
 */
@Service
public class FotoJugadorService {

    private static final Logger log = LoggerFactory.getLogger(FotoJugadorService.class);
    /** Ancho de la miniatura: de sobra para el avatar más grande de la web (104 px) en pantallas 2x. */
    static final int ANCHO = 256;

    private final LeaguepediaFotosClient leaguepedia;
    private final JugadorRealRepository jugadores;
    private final FotoJugadorRepository fotos;
    private final ActividadService actividad;
    private final TransactionTemplate tx;

    public FotoJugadorService(LeaguepediaFotosClient leaguepedia, JugadorRealRepository jugadores,
                              FotoJugadorRepository fotos, ActividadService actividad,
                              PlatformTransactionManager transacciones) {
        this.leaguepedia = leaguepedia;
        this.jugadores = jugadores;
        this.fotos = fotos;
        this.actividad = actividad;
        this.tx = new TransactionTemplate(transacciones);
    }

    /**
     * @param actualizadas fotos nuevas o cambiadas
     * @param sinCambios   jugadores cuya foto en Leaguepedia es la misma que ya teníamos
     * @param sinFoto      nicknames que Leaguepedia no tiene (o no encontramos)
     * @param errores      "nickname: motivo" de las descargas que fallaron
     */
    public record ResultadoFotos(int revisados, int actualizadas, int sinCambios, List<String> sinFoto,
                                 List<String> errores) {
    }

    /**
     * Estado de la última vez que se trajeron fotos (lo consulta el panel mientras espera).
     * resultado o error se rellenan al terminar.
     */
    public record EstadoFotos(boolean enCurso, LocalDateTime inicio, LocalDateTime fin, ResultadoFotos resultado,
                              String error) {
    }

    private final AtomicBoolean enCurso = new AtomicBoolean();
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fotos-leaguepedia");
        t.setDaemon(true);
        return t;
    });
    private volatile EstadoFotos ultimo = new EstadoFotos(false, null, null, null, null);

    public EstadoFotos estado() {
        return ultimo;
    }

    /**
     * Botón del panel: lanza la sincronización en segundo plano y vuelve enseguida, porque con el límite de
     * Leaguepedia puede tardar varios minutos. El resultado se consulta con {@link #estado()}.
     */
    public EstadoFotos lanzar(AuthUser admin, boolean todas) {
        if (!enCurso.compareAndSet(false, true)) {
            throw ApiException.conflicto("Ya se están trayendo las fotos; espera a que termine");
        }
        LocalDateTime inicio = ahora();
        ultimo = new EstadoFotos(true, inicio, null, null, null);
        try {
            hilo.execute(() -> {
                try {
                    ultimo = new EstadoFotos(false, inicio, ahora(), sincronizar(admin, todas), null);
                } catch (RuntimeException e) {
                    log.warn("No se pudieron traer las fotos de Leaguepedia", e);
                    ultimo = new EstadoFotos(false, inicio, ahora(), null, e.getMessage());
                } finally {
                    enCurso.set(false);
                }
            });
        } catch (RuntimeException e) {
            enCurso.set(false);
            throw e;
        }
        return ultimo;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Lo que sirve GET /api/jugadores/{id}/foto. */
    @Transactional(readOnly = true)
    public FotoJugador foto(Long jugadorId) {
        return fotos.findById(jugadorId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Este jugador no tiene foto"));
    }

    /** true si algún jugador no tiene ni foto descargada ni URL puesta a mano. */
    public boolean faltanFotos() {
        return jugadores.findAll().stream().anyMatch(FotoJugadorService::sinNingunaFoto);
    }

    /**
     * @param todas false = solo los jugadores sin foto; true = también comprueba si ha cambiado la de los
     *              demás (p. ej. al empezar un split nuevo) y sustituye las URL puestas a mano.
     */
    ResultadoFotos sincronizar(AuthUser admin, boolean todas) {
        List<JugadorReal> lista = jugadores.findAll().stream()
                .filter(j -> todas || sinNingunaFoto(j))
                .toList();
        List<String> sinFoto = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        int actualizadas = 0;
        int sinCambios = 0;

        if (!lista.isEmpty()) {
            Map<Long, String> pagina = lista.stream()
                    .collect(Collectors.toMap(JugadorReal::getId, FotoJugadorService::paginaLeaguepedia));
            Map<String, String> archivos = leaguepedia.archivos(new LinkedHashSet<>(pagina.values()));
            Map<Long, String> origenActual = fotos.findAllProjectedBy().stream()
                    .filter(o -> o.getArchivoOrigen() != null)
                    .collect(Collectors.toMap(FotoJugadorRepository.Origen::getJugadorId,
                            FotoJugadorRepository.Origen::getArchivoOrigen));

            // 1ª pasada: URL calculada (sin gastar consultas a la API)
            Map<JugadorReal, String> pendientes = new LinkedHashMap<>();
            for (JugadorReal j : lista) {
                String archivo = archivos.get(clave(pagina.get(j.getId())));
                if (archivo == null) {
                    sinFoto.add(j.getNickname());
                } else if (j.getFotoVersion() != null && archivo.equals(origenActual.get(j.getId()))) {
                    sinCambios++;
                } else if (descargarYGuardar(j, archivo, leaguepedia.urlMiniatura(archivo, ANCHO), null)) {
                    actualizadas++;
                } else {
                    pendientes.put(j, archivo);
                }
            }

            // 2ª pasada, solo si alguna ha fallado: la URL que da la API (imageinfo)
            if (!pendientes.isEmpty()) {
                Map<String, String> urls = leaguepedia.urls(new LinkedHashSet<>(pendientes.values()), ANCHO);
                for (var e : pendientes.entrySet()) {
                    String url = urls.get(e.getValue());
                    if (url == null) {
                        sinFoto.add(e.getKey().getNickname());
                    } else if (descargarYGuardar(e.getKey(), e.getValue(), url, errores)) {
                        actualizadas++;
                    }
                }
            }
        }

        ResultadoFotos r = new ResultadoFotos(lista.size(), actualizadas, sinCambios, sinFoto, errores);
        if (admin != null || actualizadas > 0) {
            actividad.registrar(admin, TipoActividad.IMPORTACION, (admin == null ? "El sistema" : admin.nombre())
                    + " trajo de Leaguepedia " + actualizadas + " foto(s) de jugadores (" + sinFoto.size()
                    + " sin foto en la wiki" + (errores.isEmpty() ? "" : ", " + errores.size() + " con error") + ")");
        }
        return r;
    }

    /** Descarga y guarda. Si falla devuelve false y, si se pasa la lista, apunta el motivo. */
    private boolean descargarYGuardar(JugadorReal j, String archivo, String url, List<String> errores) {
        try {
            guardar(j.getId(), archivo, leaguepedia.descargar(url));
            return true;
        } catch (RuntimeException e) {
            log.info("Foto de {} no disponible en {} ({})", j.getNickname(), url, e.getMessage());
            if (errores != null) {
                errores.add(j.getNickname() + ": " + e.getMessage());
            }
            return false;
        }
    }

    private void guardar(Long jugadorId, String archivo, LeaguepediaFotosClient.Foto descargada) {
        tx.executeWithoutResult(estado -> {
            JugadorReal j = jugadores.findById(jugadorId).orElseThrow(() -> ApiException.noEncontrado("Jugador"));
            FotoJugador f = fotos.findById(jugadorId).orElseGet(() -> {
                FotoJugador nueva = new FotoJugador();
                nueva.setJugadorId(jugadorId);
                return nueva;
            });
            f.setContenido(descargada.contenido());
            f.setTipoContenido(descargada.tipo());
            f.setArchivoOrigen(archivo);
            f.setActualizada(LocalDateTime.now(ZoneOffset.UTC));
            fotos.save(f);
            j.setFotoVersion(System.currentTimeMillis());
        });
    }

    private static boolean sinNingunaFoto(JugadorReal j) {
        return j.getFotoVersion() == null && j.getFotoUrl() == null;
    }

    /**
     * Página del jugador en Leaguepedia: viene en el id externo de la importación ("LP:Caps").
     * Para los creados a mano o desde Oracle's Elixir se prueba con el nickname.
     */
    static String paginaLeaguepedia(JugadorReal j) {
        String id = j.getIdExterno();
        return id != null && id.startsWith("LP:") && id.length() > 3 ? id.substring(3) : j.getNickname();
    }

    private static String clave(String nombre) {
        return nombre.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
