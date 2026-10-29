package com.lecfantasy.repository;

import com.lecfantasy.domain.EstadisticaJugadorPartida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface EstadisticaRepository extends JpaRepository<EstadisticaJugadorPartida, Long> {

    @Query("""
            select e from EstadisticaJugadorPartida e join fetch e.jugador left join fetch e.equipoPro
            where e.partida.id = :partidaId
            """)
    List<EstadisticaJugadorPartida> findByPartida(@Param("partidaId") Long partidaId);

    @Query("""
            select e from EstadisticaJugadorPartida e join fetch e.jugador join fetch e.partida p
            where p.jornada.id = :jornadaId
            """)
    List<EstadisticaJugadorPartida> findByJornada(@Param("jornadaId") Long jornadaId);

    @Query("""
            select e from EstadisticaJugadorPartida e join fetch e.partida p join fetch p.jornada
            where e.jugador.id = :jugadorId order by p.fechaHora desc
            """)
    List<EstadisticaJugadorPartida> findByJugador(@Param("jugadorId") Long jugadorId);

    /** Veces que se ha elegido un campeón en partidas anteriores a una fecha (mejora Inventor). */
    @Query("""
            select count(e) from EstadisticaJugadorPartida e
            where lower(e.campeon) = lower(:campeon) and e.partida.fechaHora < :antes
            """)
    long vecesCampeonAntes(@Param("campeon") String campeon, @Param("antes") LocalDateTime antes);

    @Query("select coalesce(sum(e.puntosFantasy), 0) from EstadisticaJugadorPartida e where e.jugador.id = :jugadorId")
    BigDecimal puntosTotalesJugador(@Param("jugadorId") Long jugadorId);

    /** [idJugador, sumaPuntos, partidas] de todos los jugadores (para el listado del mercado). */
    @Query("select e.jugador.id, sum(e.puntosFantasy), count(e) from EstadisticaJugadorPartida e group by e.jugador.id")
    List<Object[]> resumenPorJugador();
}
