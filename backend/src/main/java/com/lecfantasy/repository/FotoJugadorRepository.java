package com.lecfantasy.repository;

import com.lecfantasy.domain.FotoJugador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FotoJugadorRepository extends JpaRepository<FotoJugador, Long> {

    /** Solo el archivo de origen de cada foto, sin cargar las imágenes. */
    interface Origen {
        Long getJugadorId();

        String getArchivoOrigen();
    }

    List<Origen> findAllProjectedBy();
}
