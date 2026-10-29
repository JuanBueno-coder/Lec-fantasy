package com.lecfantasy.domain;

/**
 * Cuándo se cumple una mejora. Se evalúa en cada partida de la jornada por separado.
 * <ul>
 *   <li>SIEMPRE: en todas las partidas que juegue (Capitán, Good death).</li>
 *   <li>ESTADISTICA: "estadística comparador umbral", p. ej. MUERTES MENOR 3 (Inmortal).</li>
 *   <li>CAMPEON_POCO_JUGADO: su campeón se ha elegido como mucho "umbral" veces en la LEC
 *       antes de esa partida (Inventor).</li>
 *   <li>Retos concretos: triple / quadra / pentakill, first blood, MVP o victoria.</li>
 * </ul>
 */
public enum CondicionMejora {
    SIEMPRE, ESTADISTICA, CAMPEON_POCO_JUGADO, TRIPLE_KILL, QUADRA_KILL, PENTA_KILL, FIRST_BLOOD, MVP, VICTORIA;

    public boolean necesitaUmbral() {
        return this == ESTADISTICA || this == CAMPEON_POCO_JUGADO;
    }
}
