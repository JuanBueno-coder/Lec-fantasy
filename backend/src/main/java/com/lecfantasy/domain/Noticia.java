package com.lecfantasy.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "noticia")
@Getter
@Setter
@NoArgsConstructor
public class Noticia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_noticia")
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 500)
    private String resumen;

    @Column(nullable = false, length = 10000)
    private String contenido;

    private String imagenUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_autor")
    private Usuario autor;

    private boolean publicada = true;

    @Column(nullable = false)
    private LocalDateTime fechaPublicacion;

    /** MANUAL (la escribe un administrador) o el canal oficial del que se importó. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenNoticia origen = OrigenNoticia.MANUAL;

    /** Id de la publicación en su red (tuit, vídeo): evita importarla dos veces. */
    @Column(length = 100)
    private String idExterno;

    /** Enlace a la publicación original. */
    @Column(length = 500)
    private String urlExterna;

    /** Cuenta oficial que la publicó, p. ej. "@LEC". */
    @Column(length = 100)
    private String autorExterno;
}
