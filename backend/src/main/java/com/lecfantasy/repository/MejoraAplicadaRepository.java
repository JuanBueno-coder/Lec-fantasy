package com.lecfantasy.repository;

import com.lecfantasy.domain.EstadoMejora;
import com.lecfantasy.domain.MejoraAplicada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MejoraAplicadaRepository extends JpaRepository<MejoraAplicada, Long> {

    boolean existsByEquipoIdAndJugadorIdAndJornadaId(Long equipoId, Long jugadorId, Long jornadaId);

    /** Para el límite por equipo (p. ej. un solo Capitán): las que tiene sin usar. */
    long countByEquipoIdAndMejoraIdAndEstadoIn(Long equipoId, Long mejoraId, Collection<EstadoMejora> estados);

    @Query("""
            select m from MejoraAplicada m join fetch m.mejora left join fetch m.jugador left join fetch m.jornada
            where m.equipo.id = :equipoId order by m.id desc
            """)
    List<MejoraAplicada> findByEquipo(@Param("equipoId") Long equipoId);

    @Query("""
            select m from MejoraAplicada m join fetch m.jugador
            where m.equipo.id = :equipoId and m.estado = :estado
            """)
    List<MejoraAplicada> findByEquipoYEstado(@Param("equipoId") Long equipoId, @Param("estado") EstadoMejora estado);

    @Query("""
            select m from MejoraAplicada m join fetch m.mejora join fetch m.jugador join fetch m.equipo
            where m.jornada.id = :jornadaId and m.estado = :estado
            """)
    List<MejoraAplicada> findByJornadaYEstado(@Param("jornadaId") Long jornadaId, @Param("estado") EstadoMejora estado);
}
