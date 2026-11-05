package com.lecfantasy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Foto de un jugador descargada de Leaguepedia. Una por jugador; la clave es la del jugador. */
@Entity
@Table(name = "foto_jugador")
@Getter
@Setter
@NoArgsConstructor
public class FotoJugador {

    @Id
    @Column(name = "id_jugador_real")
    private Long jugadorId;

    @Lob
    @Column(nullable = false)
    private byte[] contenido;

    @Column(nullable = false, length = 60)
    private String tipoContenido;

    /** Nombre del archivo en Leaguepedia (p. ej. "G2 Caps 2026 Split 1.png"). */
    private String archivoOrigen;

    @Column(nullable = false)
    private LocalDateTime actualizada;
}
