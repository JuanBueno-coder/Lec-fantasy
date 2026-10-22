package com.lecfantasy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "jugador_real")
@Getter
@Setter
@NoArgsConstructor
public class JugadorReal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_jugador_real")
    private Long id;

    @Column(nullable = false, length = 40)
    private String nickname;

    private String nombreReal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipo_pro")
    private EquipoPro equipoPro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Posicion posicion;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal valorMercado;

    private String fotoUrl;

    /** Identificador en la fuente externa (Leaguepedia / Oracle's Elixir). */
    private String idExterno;

    private boolean bloqueado;

    private String motivoBloqueo;
}
