package com.lecfantasy.service;

import com.lecfantasy.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Evalúa una mejora sobre las partidas de un jugador en la jornada. Cada partida se mira
 * por separado: si en ella se cumple la condición, se aplica el efecto a los puntos de esa
 * partida. La mejora cuenta como CUMPLIDA si se cumple en al menos una.
 */
public final class EvaluadorMejoras {

    /** Puntos extra que da la mejora y si se ha cumplido en alguna partida. */
    public record Resultado(boolean cumplida, BigDecimal puntosExtra) {
        static final Resultado FALLIDA = new Resultado(false, BigDecimal.ZERO);
    }

    private EvaluadorMejoras() {
    }

    /**
     * @param vecesCampeonAntes cuántas veces se había elegido en la LEC el campeón de esa
     *                          estadística antes de la partida (solo para CAMPEON_POCO_JUGADO)
     */
    public static Resultado evaluar(Mejora mejora, List<EstadisticaJugadorPartida> partidas,
                                    ToLongFunction<EstadisticaJugadorPartida> vecesCampeonAntes) {
        if (partidas.isEmpty()) {
            return Resultado.FALLIDA; // si no juega, la mejora falla
        }
        boolean cumplida = false;
        BigDecimal extra = BigDecimal.ZERO;
        for (EstadisticaJugadorPartida e : partidas) {
            if (cumple(mejora, e, vecesCampeonAntes)) {
                cumplida = true;
                extra = extra.add(efecto(mejora, e));
            }
        }
        return cumplida ? new Resultado(true, extra.setScale(2, RoundingMode.HALF_UP)) : Resultado.FALLIDA;
    }

    /** ¿Se cumple la condición en esta partida? */
    public static boolean cumple(Mejora m, EstadisticaJugadorPartida e,
                                 ToLongFunction<EstadisticaJugadorPartida> vecesCampeonAntes) {
        return switch (m.getCondicion()) {
            case SIEMPRE -> true;
            case ESTADISTICA -> m.getEstadistica() != null && m.getComparador() != null && m.getUmbral() != null
                    && m.getComparador().compara(m.getEstadistica().valor(e), m.getUmbral());
            case CAMPEON_POCO_JUGADO -> e.getCampeon() != null && !e.getCampeon().isBlank() && m.getUmbral() != null
                    && BigDecimal.valueOf(vecesCampeonAntes.applyAsLong(e)).compareTo(m.getUmbral()) <= 0;
            case TRIPLE_KILL -> e.getTripleKills() + e.getQuadraKills() + e.getPentaKills() > 0;
            case QUADRA_KILL -> e.getQuadraKills() + e.getPentaKills() > 0;
            case PENTA_KILL -> e.getPentaKills() > 0;
            case FIRST_BLOOD -> e.isFirstBlood();
            case MVP -> e.isMvp();
            case VICTORIA -> e.isVictoria();
        };
    }

    /** Puntos extra que da la mejora en una partida en la que se cumple (puede ser negativo). */
    public static BigDecimal efecto(Mejora m, EstadisticaJugadorPartida e) {
        if (m.getEfecto() == EfectoMejora.PUNTOS_POR_ACCION) {
            EstadisticaMejora est = m.getEfectoEstadistica();
            if (est == null || !est.admiteEfecto() || m.getPuntosPorUnidad() == null) {
                return BigDecimal.ZERO;
            }
            // Good death: 4 muertes · (−0,5 − (−1)) = +2 puntos
            return est.valor(e).multiply(m.getPuntosPorUnidad().subtract(est.puntosNormales()));
        }
        return e.getPuntosFantasy().multiply(m.getMultiplicador().subtract(BigDecimal.ONE));
    }
}
