package com.lecfantasy.service.calendario;

import com.lecfantasy.config.AppProperties;
import com.lecfantasy.domain.EquipoPro;
import com.lecfantasy.dto.JornadaDtos.JornadaDto;
import com.lecfantasy.dto.JornadaDtos.JornadaRequest;
import com.lecfantasy.repository.EquipoProRepository;
import com.lecfantasy.service.JornadaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calendario de la LEC a partir de PandaScore:
 * <ul>
 *   <li>Próximos partidos para el panel (con caché de 15 minutos: el plan gratuito permite
 *       1.000 peticiones por hora y así no se gasta una por cada visita).</li>
 *   <li>Creación automática de jornadas: una por cada semana con partidos (de lunes a domingo,
 *       hora de Madrid), desde el primer hasta el último partido de esa semana.</li>
 *   <li>Logos y siglas de los equipos que todavía no los tengan.</li>
 * </ul>
 */
@Service
public class CalendarioService {

    private static final Logger log = LoggerFactory.getLogger(CalendarioService.class);
    private static final Duration CACHE = Duration.ofMinutes(15);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final PandaScoreClient pandascore;
    private final JornadaService jornadas;
    private final EquipoProRepository equiposPro;
    private final AppProperties.PandaScore config;
    private final ZoneId zona;

    private volatile List<PartidoCalendario> cache = List.of();
    private volatile Instant cacheHasta = Instant.EPOCH;

    public CalendarioService(PandaScoreClient pandascore, JornadaService jornadas, EquipoProRepository equiposPro,
                             AppProperties props) {
        this.pandascore = pandascore;
        this.jornadas = jornadas;
        this.equiposPro = equiposPro;
        this.config = props.pandascore();
        this.zona = ZoneId.of(props.automatizacion().zona());
    }

    public boolean disponible() {
        return pandascore.configurado();
    }

    /**
     * Partidos de las últimas 12 horas y los próximos días (para ver también los que se están
     * jugando o acaban de terminar). Si PandaScore falla devuelve lo último que tenía.
     */
    public List<PartidoCalendario> proximos() {
        if (!disponible()) {
            return List.of();
        }
        if (Instant.now().isBefore(cacheHasta)) {
            return cache;
        }
        Instant ahora = Instant.now();
        try {
            cache = List.copyOf(pandascore.partidos(ahora.minus(Duration.ofHours(12)),
                    ahora.plus(Duration.ofDays(config.diasCalendario()))));
            cacheHasta = ahora.plus(CACHE);
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el calendario de PandaScore: {}", e.getMessage());
            cacheHasta = ahora.plus(Duration.ofMinutes(2)); // no reintentar en cada visita
        }
        return cache;
    }

    /** Crea las jornadas que falten para las próximas semanas con partidos. Devuelve lo que ha hecho. */
    public List<String> crearJornadas() {
        List<String> acciones = new ArrayList<>();
        if (!disponible()) {
            return acciones;
        }
        Instant ahora = Instant.now();
        List<PartidoCalendario> partidos = pandascore.partidos(ahora, ahora.plus(Duration.ofDays(config.diasCalendario())))
                .stream().filter(p -> !p.cancelado()).toList();
        actualizarEquipos(partidos);
        int logos = completarLogosDelUltimoAnio(ahora);
        if (logos > 0) {
            acciones.add("Logos y siglas copiados de PandaScore a " + logos + " equipo(s).");
        }

        LocalDate hoy = LocalDate.now(zona);
        Map<LocalDate, List<PartidoCalendario>> porSemana = partidos.stream().collect(Collectors.groupingBy(
                p -> dia(p).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), TreeMap::new, Collectors.toList()));
        List<JornadaDto> existentes = new ArrayList<>(jornadas.listar());
        for (List<PartidoCalendario> semana : porSemana.values()) {
            LocalDate inicio = semana.stream().map(this::dia).min(Comparator.naturalOrder()).orElseThrow();
            LocalDate fin = semana.stream().map(this::dia).max(Comparator.naturalOrder()).orElseThrow();
            if (inicio.isBefore(hoy)) {
                continue; // semana ya empezada: la gestiona la jornada que haya (o el admin)
            }
            boolean solapa = existentes.stream()
                    .anyMatch(j -> !j.fechaInicio().isAfter(fin) && !j.fechaFin().isBefore(inicio));
            if (solapa) {
                continue;
            }
            String competicion = semana.get(0).competicion();
            String nombre = "Jornada " + (existentes.size() + 1) + (competicion == null ? "" : " · " + competicion);
            JornadaDto nueva = jornadas.crear(null, new JornadaRequest(
                    nombre.length() > 100 ? nombre.substring(0, 100) : nombre, inicio, fin, null));
            existentes.add(nueva);
            acciones.add("«" + nueva.nombre() + "» creada desde el calendario de PandaScore ("
                    + inicio.format(DIA) + " – " + fin.format(DIA) + ", " + semana.size() + " partidos).");
        }
        return acciones;
    }

    /**
     * Si queda algún equipo sin logo (p. ej. los creados al importar de Leaguepedia en pretemporada,
     * cuando no hay próximos partidos), busca sus logos en los partidos de la LEC del último año.
     * Solo hace la consulta si hace falta, para no gastar peticiones del plan gratuito.
     */
    int completarLogosDelUltimoAnio(Instant ahora) {
        boolean faltan = equiposPro.findAll().stream().anyMatch(e -> e.getLogoUrl() == null || e.getSiglas() == null);
        if (!faltan) {
            return 0;
        }
        return actualizarEquipos(pandascore.partidos(ahora.minus(Duration.ofDays(365)), ahora));
    }

    /** Copia logo y siglas de PandaScore a los equipos que no los tengan (se buscan por nombre o siglas). */
    int actualizarEquipos(List<PartidoCalendario> partidos) {
        Map<String, PartidoCalendario.Equipo> vistos = new HashMap<>();
        partidos.forEach(p -> {
            vistos.putIfAbsent(p.equipoA().nombre(), p.equipoA());
            vistos.putIfAbsent(p.equipoB().nombre(), p.equipoB());
        });
        List<EquipoPro> todos = equiposPro.findAll();
        Set<EquipoPro> cambiados = new HashSet<>();
        for (PartidoCalendario.Equipo e : vistos.values()) {
            todos.stream()
                    .filter(ep -> normalizar(ep.getNombre()).equals(normalizar(e.nombre()))
                            || (ep.getSiglas() != null && e.siglas() != null && ep.getSiglas().equalsIgnoreCase(e.siglas())))
                    .findFirst()
                    .ifPresent(ep -> {
                        if (ep.getLogoUrl() == null && e.logoUrl() != null) {
                            ep.setLogoUrl(e.logoUrl());
                            cambiados.add(ep);
                        }
                        if (ep.getSiglas() == null && e.siglas() != null) {
                            ep.setSiglas(e.siglas());
                            cambiados.add(ep);
                        }
                    });
        }
        equiposPro.saveAll(cambiados);
        return cambiados.size();
    }

    /** "GIANTX", "GiantX" y "Giant X" cuentan como el mismo equipo. */
    static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private LocalDate dia(PartidoCalendario p) {
        return p.fecha().atZone(zona).toLocalDate();
    }
}
