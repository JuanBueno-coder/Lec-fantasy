package com.lecfantasy.domain;

import com.lecfantasy.service.Puntuacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;

/**
 * Estadística de una partida que pueden usar las mejoras, como condición
 * ("muere menos de 3 veces") o como efecto ("cada muerte vale −0,5").
 * {@code puntosNormales} es lo que vale cada unidad en {@link Puntuacion}; null si no
 * se puede usar como efecto (KDA y puntos son calculados, no acciones).
 */
public enum EstadisticaMejora {
    KILLS("kills", e -> BigDecimal.valueOf(e.getKills()), Puntuacion.KILL),
    MUERTES("muertes", e -> BigDecimal.valueOf(e.getDeaths()), Puntuacion.DEATH),
    ASISTENCIAS("asistencias", e -> BigDecimal.valueOf(e.getAssists()), Puntuacion.ASSIST),
    CS("CS", e -> BigDecimal.valueOf(e.getCs()), Puntuacion.POR_CS),
    VISION("visión", e -> BigDecimal.valueOf(e.getVisionScore()), Puntuacion.POR_VISION),
    DOBLES("double kills", e -> BigDecimal.valueOf(e.getDoubleKills()), Puntuacion.DOUBLE_KILL),
    TRIPLES("triple kills", e -> BigDecimal.valueOf(e.getTripleKills()), Puntuacion.TRIPLE_KILL),
    QUADRAS("quadra kills", e -> BigDecimal.valueOf(e.getQuadraKills()), Puntuacion.QUADRA_KILL),
    PENTAS("pentakills", e -> BigDecimal.valueOf(e.getPentaKills()), Puntuacion.PENTA_KILL),
    KDA("KDA", e -> BigDecimal.valueOf(e.getKills() + e.getAssists())
            .divide(BigDecimal.valueOf(Math.max(1, e.getDeaths())), 2, RoundingMode.HALF_UP), null),
    PUNTOS("puntos", EstadisticaJugadorPartida::getPuntosFantasy, null);

    private final String texto;
    private final Function<EstadisticaJugadorPartida, BigDecimal> valor;
    private final BigDecimal puntosNormales;

    EstadisticaMejora(String texto, Function<EstadisticaJugadorPartida, BigDecimal> valor, BigDecimal puntosNormales) {
        this.texto = texto;
        this.valor = valor;
        this.puntosNormales = puntosNormales;
    }

    public String texto() {
        return texto;
    }

    public BigDecimal valor(EstadisticaJugadorPartida e) {
        return valor.apply(e);
    }

    /** Lo que vale cada unidad sin mejoras, o null si no admite efecto de puntos por acción. */
    public BigDecimal puntosNormales() {
        return puntosNormales;
    }

    public boolean admiteEfecto() {
        return puntosNormales != null;
    }
}
