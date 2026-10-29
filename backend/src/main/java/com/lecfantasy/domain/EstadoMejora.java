package com.lecfantasy.domain;

/**
 * INVENTARIO: comprada, sin usar. PENDIENTE: asignada a un titular para la próxima jornada.
 * CUMPLIDA / FALLIDA: ya usada (una mejora solo tiene un uso).
 */
public enum EstadoMejora {
    INVENTARIO, PENDIENTE, CUMPLIDA, FALLIDA
}
