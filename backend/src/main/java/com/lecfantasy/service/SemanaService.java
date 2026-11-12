package com.lecfantasy.service;

import com.lecfantasy.domain.EstadisticaJugadorPartida;
import com.lecfantasy.domain.EstadoJornada;
import com.lecfantasy.domain.Jornada;
import com.lecfantasy.dto.CatalogoDtos.JugadorDto;
import com.lecfantasy.dto.JornadaDtos.JornadaDto;
import com.lecfantasy.dto.SemanaDtos.SemanaDto;
import com.lecfantasy.dto.SemanaDtos.TopJugadorDto;
import com.lecfantasy.dto.SemanaDtos.VariacionDto;
import com.lecfantasy.repository.EstadisticaRepository;
import com.lecfantasy.repository.JornadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Datos de «La semana en la LEC» de la pantalla de inicio. */
@Service
public class SemanaService {

    static final int TAMANO_TOP = 5;
    static final int TAMANO_PRECIOS = 3;
    /** Cuántas jornadas hacia atrás se buscan estadísticas. */
    private static final int JORNADAS_A_MIRAR = 4;

    private final JornadaRepository jornadas;
    private final EstadisticaRepository estadisticas;
    private final JugadorService jugadores;

    public SemanaService(JornadaRepository jornadas, EstadisticaRepository estadisticas, JugadorService jugadores) {
        this.jornadas = jornadas;
        this.estadisticas = estadisticas;
        this.jugadores = jugadores;
    }

    @Transactional(readOnly = true)
    public SemanaDto semana() {
        Map<Long, JugadorDto> porId = jugadores.buscar(null, null, null, true).stream()
                .collect(Collectors.toMap(JugadorDto::id, Function.identity()));

        // Jornada de referencia: la más reciente (en curso o cerrada) que ya tenga partidas importadas
        Jornada referencia = null;
        List<EstadisticaJugadorPartida> stats = List.of();
        List<Jornada> jugadas = new ArrayList<>(jornadas.findAllByOrderByFechaInicioAsc().stream()
                .filter(j -> j.getEstado() != EstadoJornada.PROGRAMADA).toList());
        Collections.reverse(jugadas);
        for (Jornada j : jugadas.subList(0, Math.min(JORNADAS_A_MIRAR, jugadas.size()))) {
            stats = estadisticas.findByJornada(j.getId());
            if (!stats.isEmpty()) {
                referencia = j;
                break;
            }
        }

        Map<Long, BigDecimal> puntos = new HashMap<>();
        Map<Long, Long> partidas = new HashMap<>();
        for (EstadisticaJugadorPartida e : stats) {
            Long id = e.getJugador().getId();
            puntos.merge(id, e.getPuntosFantasy(), BigDecimal::add);
            partidas.merge(id, 1L, Long::sum);
        }
        List<TopJugadorDto> top = puntos.entrySet().stream()
                .filter(p -> porId.containsKey(p.getKey()))
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(TAMANO_TOP)
                .map(p -> new TopJugadorDto(porId.get(p.getKey()), p.getValue(), partidas.get(p.getKey())))
                .toList();

        // Precios: variación del último cierre de jornada (valor actual frente al anterior)
        List<VariacionDto> variaciones = porId.values().stream()
                .filter(j -> !j.bloqueado() && j.valorAnterior() != null && j.valorAnterior().signum() > 0
                        && j.valorMercado() != null && j.valorMercado().compareTo(j.valorAnterior()) != 0)
                .map(j -> new VariacionDto(j, j.valorMercado().subtract(j.valorAnterior())
                        .multiply(BigDecimal.valueOf(100)).divide(j.valorAnterior(), 1, RoundingMode.HALF_UP)))
                .toList();
        List<VariacionDto> suben = variaciones.stream().filter(v -> v.porcentaje().signum() > 0)
                .sorted(Comparator.comparing(VariacionDto::porcentaje).reversed()).limit(TAMANO_PRECIOS).toList();
        List<VariacionDto> bajan = variaciones.stream().filter(v -> v.porcentaje().signum() < 0)
                .sorted(Comparator.comparing(VariacionDto::porcentaje)).limit(TAMANO_PRECIOS).toList();

        return new SemanaDto(referencia == null ? null : JornadaDto.of(referencia), top, suben, bajan);
    }
}
