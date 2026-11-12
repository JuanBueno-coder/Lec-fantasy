package com.lecfantasy.dto;

import com.lecfantasy.dto.CatalogoDtos.JugadorDto;
import com.lecfantasy.dto.JornadaDtos.JornadaDto;

import java.math.BigDecimal;
import java.util.List;

/** «La semana en la LEC»: lo que pasa en la competición, igual para todas las ligas. */
public final class SemanaDtos {

    private SemanaDtos() {
    }

    /** Puntos de un jugador en la jornada de referencia. */
    public record TopJugadorDto(JugadorDto jugador, BigDecimal puntos, long partidas) {
    }

    /** Cambio de precio en el último cierre de jornada, en %. */
    public record VariacionDto(JugadorDto jugador, BigDecimal porcentaje) {
    }

    /**
     * jornada: la última con estadísticas (la que está en curso o la última cerrada); null si aún no hay.
     * top: los 5 que más puntos hicieron en ella (el primero es el jugador de la jornada).
     */
    public record SemanaDto(JornadaDto jornada, List<TopJugadorDto> top, List<VariacionDto> suben,
                            List<VariacionDto> bajan) {
    }
}
