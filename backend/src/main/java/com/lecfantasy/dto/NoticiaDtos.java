package com.lecfantasy.dto;

import com.lecfantasy.domain.Noticia;
import com.lecfantasy.domain.OrigenNoticia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class NoticiaDtos {

    private NoticiaDtos() {
    }

    /**
     * origen: MANUAL, X o YOUTUBE. En las importadas, urlExterna enlaza a la publicación original
     * y autor es la cuenta oficial (p. ej. "@LEC").
     */
    public record NoticiaDto(Long id, String titulo, String resumen, String contenido, String imagenUrl,
                             String autor, boolean publicada, LocalDateTime fechaPublicacion,
                             OrigenNoticia origen, String urlExterna) {

        public static NoticiaDto of(Noticia n) {
            String autor = n.getAutorExterno() != null ? n.getAutorExterno()
                    : n.getAutor() == null ? null : n.getAutor().getNombre();
            return new NoticiaDto(n.getId(), n.getTitulo(), n.getResumen(), n.getContenido(), n.getImagenUrl(),
                    autor, n.isPublicada(), n.getFechaPublicacion(), n.getOrigen(), n.getUrlExterna());
        }
    }

    public record NoticiaRequest(
            @NotBlank(message = "El título es obligatorio") @Size(max = 200) String titulo,
            @Size(max = 500) String resumen,
            @NotBlank(message = "El contenido es obligatorio") @Size(max = 10000) String contenido,
            @Size(max = 500) String imagenUrl,
            boolean publicada) {
    }
}
