package com.lecfantasy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mejora del catálogo. Se configura por completo desde el panel de administración:
 * una condición (cuándo se cumple en una partida) y un efecto (qué pasa con los puntos).
 */
@Entity
@Table(name = "mejora")
@Getter
@Setter
@NoArgsConstructor
public class Mejora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mejora")
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    private String descripcion;

    /** 0 = gratis. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precio;

    // ---- Condición ----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CondicionMejora condicion;

    /** Solo con condición ESTADISTICA. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadisticaMejora estadistica;

    /** Solo con condición ESTADISTICA. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private ComparadorMejora comparador;

    /** ESTADISTICA: valor con el que se compara. CAMPEON_POCO_JUGADO: máximo de veces elegido antes. */
    @Column(precision = 6, scale = 2)
    private BigDecimal umbral;

    // ---- Efecto ----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EfectoMejora efecto = EfectoMejora.MULTIPLICADOR;

    /** Con efecto MULTIPLICADOR: los puntos de la partida se multiplican por este valor. */
    @Column(name = "multiplicador_bono", nullable = false, precision = 5, scale = 2)
    private BigDecimal multiplicador = BigDecimal.ONE;

    /** Con efecto PUNTOS_POR_ACCION: la estadística que cambia de valor... */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadisticaMejora efectoEstadistica;

    /** ...y lo que pasa a valer cada unidad (p. ej. −0,5 por muerte). */
    @Column(precision = 6, scale = 2)
    private BigDecimal puntosPorUnidad;

    /** Máximo que puede tener un equipo a la vez (en inventario o asignadas). Null = sin límite. */
    private Integer maxPorEquipo;

    private boolean activa = true;

    public boolean esGratis() {
        return precio.signum() == 0;
    }
}
