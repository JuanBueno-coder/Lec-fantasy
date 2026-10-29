package com.lecfantasy.domain;

/**
 * Qué hace una mejora cuando se cumple en una partida.
 * <ul>
 *   <li>MULTIPLICADOR: los puntos del jugador en esa partida se multiplican (×1,25, ×2...).</li>
 *   <li>PUNTOS_POR_ACCION: cada unidad de una estadística pasa a valer otra cantidad
 *       (Good death: cada muerte −0,5 en lugar de −1).</li>
 * </ul>
 */
public enum EfectoMejora {
    MULTIPLICADOR, PUNTOS_POR_ACCION
}
