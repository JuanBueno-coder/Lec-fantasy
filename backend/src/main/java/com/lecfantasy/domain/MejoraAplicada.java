package com.lecfantasy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mejora que tiene un equipo fantasy. Ciclo de vida:
 * INVENTARIO (comprada) → PENDIENTE (asignada a un titular para la próxima jornada) →
 * CUMPLIDA / FALLIDA (usada al cerrar la jornada). Si el jugador deja de ser titular antes
 * del cierre, vuelve al INVENTARIO sin gastarse.
 */
@Entity
@Table(name = "mejora_aplicada")
@Getter
@Setter
@NoArgsConstructor
public class MejoraAplicada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mejora_aplicada")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_equipo_fantasy")
    private EquipoFantasy equipo;

    /** Null mientras está en el inventario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jugador_real")
    private JugadorReal jugador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_mejora")
    private Mejora mejora;

    /** Null mientras está en el inventario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_jornada")
    private Jornada jornada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoMejora estado = EstadoMejora.INVENTARIO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal precioPagado;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal puntosExtra = BigDecimal.ZERO;

    private LocalDateTime fechaCompra;

    public void asignar(JugadorReal jugador, Jornada jornada) {
        this.jugador = jugador;
        this.jornada = jornada;
        this.estado = EstadoMejora.PENDIENTE;
    }

    /** Vuelve al inventario sin gastarse. */
    public void devolverAlInventario() {
        this.jugador = null;
        this.jornada = null;
        this.estado = EstadoMejora.INVENTARIO;
        this.puntosExtra = BigDecimal.ZERO;
    }

    public boolean usada() {
        return estado == EstadoMejora.CUMPLIDA || estado == EstadoMejora.FALLIDA;
    }
}
