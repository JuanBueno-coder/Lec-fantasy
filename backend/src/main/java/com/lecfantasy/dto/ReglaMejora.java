package com.lecfantasy.dto;

import com.lecfantasy.domain.EfectoMejora;
import com.lecfantasy.domain.EstadisticaMejora;
import com.lecfantasy.domain.Mejora;

import java.math.BigDecimal;

/**
 * Explica una mejora en una frase, a partir de su condición y su efecto, para que la web
 * y la app muestren lo mismo: "Si muertes < 3 en una partida: puntos de esa partida ×1,5".
 */
public final class ReglaMejora {

    private ReglaMejora() {
    }

    public static String texto(Mejora m) {
        return condicion(m) + ": " + efecto(m);
    }

    static String condicion(Mejora m) {
        return switch (m.getCondicion()) {
            case SIEMPRE -> "En todas sus partidas de la jornada";
            case ESTADISTICA -> m.getEstadistica() == null || m.getComparador() == null
                    ? "Condición sin configurar"
                    : "Si " + m.getEstadistica().texto() + " " + m.getComparador().simbolo() + " "
                      + numero(m.getUmbral()) + " en una partida";
            case CAMPEON_POCO_JUGADO -> "Si juega un campeón elegido " + numero(m.getUmbral())
                    + " veces o menos en la LEC antes de esa partida";
            case TRIPLE_KILL -> "Si hace una triple kill (o más) en una partida";
            case QUADRA_KILL -> "Si hace una quadra kill (o más) en una partida";
            case PENTA_KILL -> "Si hace una pentakill en una partida";
            case FIRST_BLOOD -> "Si participa en la primera sangre de una partida";
            case MVP -> "Si es el MVP de una partida";
            case VICTORIA -> "Si su equipo gana una partida";
        };
    }

    static String efecto(Mejora m) {
        if (m.getEfecto() == EfectoMejora.PUNTOS_POR_ACCION) {
            EstadisticaMejora est = m.getEfectoEstadistica();
            if (est == null || !est.admiteEfecto()) {
                return "efecto sin configurar";
            }
            return "cada unidad de " + est.texto() + " vale " + conSigno(m.getPuntosPorUnidad())
                   + " (en lugar de " + conSigno(est.puntosNormales()) + ")";
        }
        return "puntos de esa partida ×" + numero(m.getMultiplicador());
    }

    /** 1.50 → "1,5"; 3.00 → "3". */
    static String numero(BigDecimal v) {
        if (v == null) {
            return "?";
        }
        BigDecimal limpio = v.stripTrailingZeros();
        if (limpio.scale() < 0) {
            limpio = limpio.setScale(0);
        }
        return limpio.toPlainString().replace('.', ',');
    }

    static String conSigno(BigDecimal v) {
        if (v == null) {
            return "?";
        }
        return (v.signum() > 0 ? "+" : v.signum() < 0 ? "−" : "") + numero(v.abs());
    }
}
