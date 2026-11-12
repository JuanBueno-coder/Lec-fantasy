package com.lecfantasy.repository;

import com.lecfantasy.domain.Noticia;
import com.lecfantasy.domain.OrigenNoticia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticiaRepository extends JpaRepository<Noticia, Long> {

    Page<Noticia> findByPublicadaTrueOrderByFechaPublicacionDesc(Pageable pageable);

    Page<Noticia> findAllByOrderByFechaPublicacionDesc(Pageable pageable);

    boolean existsByOrigenAndIdExterno(OrigenNoticia origen, String idExterno);

    /** La última publicación importada de una cuenta (para pedir a X solo las posteriores). */
    Optional<Noticia> findFirstByOrigenAndAutorExternoOrderByFechaPublicacionDesc(OrigenNoticia origen, String autorExterno);
}
