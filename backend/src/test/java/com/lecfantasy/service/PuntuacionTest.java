package com.lecfantasy.service;

import com.lecfantasy.domain.*;
import com.lecfantasy.service.importer.ImportService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PuntuacionTest {

    private static EstadisticaJugadorPartida linea(int k, int d, int a, int cs, int vision, boolean victoria) {
        EstadisticaJugadorPartida e = new EstadisticaJugadorPartida();
        e.setKills(k);
        e.setDeaths(d);
        e.setAssists(a);
        e.setCs(cs);
        e.setVisionScore(vision);
        e.setVictoria(victoria);
        return e;
    }

    @Test
    void ejemploDeLaDocumentacion() {
        // ADC 6/2/8, 300 CS, 30 de visión y victoria = 41,5
        assertThat(Puntuacion.base(linea(6, 2, 8, 300, 30, true))).isEqualByComparingTo("41.5");
    }

    @Test
    void multikillsYFirstBloodSuman() {
        EstadisticaJugadorPartida e = linea(0, 0, 0, 0, 0, false);
        e.setDoubleKills(1);
        e.setTripleKills(1);
        e.setQuadraKills(1);
        e.setPentaKills(1);
        e.setFirstBlood(true);
        assertThat(Puntuacion.base(e)).isEqualByComparingTo("20"); // 1 + 2 + 5 + 10 + 2
    }

    @Test
    void elMvpEsElDeMasPuntosYSumaTres() {
        EstadisticaJugadorPartida flojo = linea(1, 3, 2, 100, 10, false);
        EstadisticaJugadorPartida bueno = linea(8, 0, 4, 300, 20, true);
        ImportService.asignarMvpYPuntos(List.of(flojo, bueno));
        assertThat(bueno.isMvp()).isTrue();
        assertThat(flojo.isMvp()).isFalse();
        assertThat(bueno.getPuntosFantasy()).isEqualByComparingTo(Puntuacion.base(bueno).add(Puntuacion.MVP));
    }

    private static final java.util.function.ToLongFunction<EstadisticaJugadorPartida> NUNCA = e -> 0;

    private static Mejora multiplica(CondicionMejora c, String mult) {
        Mejora m = new Mejora();
        m.setCondicion(c);
        m.setEfecto(EfectoMejora.MULTIPLICADOR);
        m.setMultiplicador(new BigDecimal(mult));
        return m;
    }

    private static Mejora porEstadistica(EstadisticaMejora est, ComparadorMejora cmp, String umbral, String mult) {
        Mejora m = multiplica(CondicionMejora.ESTADISTICA, mult);
        m.setEstadistica(est);
        m.setComparador(cmp);
        m.setUmbral(new BigDecimal(umbral));
        return m;
    }

    /** Partida con puntos ya calculados (como al cerrar la jornada). */
    private static EstadisticaJugadorPartida puntuada(int k, int d, int a) {
        EstadisticaJugadorPartida e = linea(k, d, a, 0, 0, false);
        e.setPuntosFantasy(Puntuacion.total(e));
        return e;
    }

    @Test
    void retosClasicosSeEvaluanPartidaAPartida() {
        Mejora triple = multiplica(CondicionMejora.TRIPLE_KILL, "2");
        Mejora kda = porEstadistica(EstadisticaMejora.KDA, ComparadorMejora.MAYOR_IGUAL, "5", "2");

        EstadisticaJugadorPartida conQuadra = linea(5, 1, 5, 0, 0, true);
        conQuadra.setQuadraKills(1);
        EstadisticaJugadorPartida normal = linea(2, 1, 1, 0, 0, true);

        assertThat(EvaluadorMejoras.cumple(triple, conQuadra, NUNCA)).isTrue();   // una quadra cuenta como triple
        assertThat(EvaluadorMejoras.cumple(triple, normal, NUNCA)).isFalse();
        assertThat(EvaluadorMejoras.cumple(kda, conQuadra, NUNCA)).isTrue();      // (5+5)/1 = 10
        assertThat(EvaluadorMejoras.cumple(kda, normal, NUNCA)).isFalse();        // (2+1)/1 = 3
        assertThat(EvaluadorMejoras.evaluar(triple, List.of(), NUNCA).cumplida()).isFalse(); // si no juega, falla
    }

    @Test
    void capitanMultiplicaTodasLasPartidas() {
        Mejora capitan = multiplica(CondicionMejora.SIEMPRE, "1.25");
        EstadisticaJugadorPartida p1 = puntuada(4, 0, 0); // 12
        EstadisticaJugadorPartida p2 = puntuada(0, 2, 4); // 6
        EvaluadorMejoras.Resultado r = EvaluadorMejoras.evaluar(capitan, List.of(p1, p2), NUNCA);
        assertThat(r.cumplida()).isTrue();
        assertThat(r.puntosExtra()).isEqualByComparingTo("4.5"); // (12 + 6) · 0,25
    }

    @Test
    void inmortalYMoradorMiranLasMuertesDeCadaPartida() {
        Mejora inmortal = porEstadistica(EstadisticaMejora.MUERTES, ComparadorMejora.MENOR, "3", "1.5");
        Mejora morador = porEstadistica(EstadisticaMejora.MUERTES, ComparadorMejora.MENOR, "5", "1.25");
        EstadisticaJugadorPartida dosMuertes = puntuada(4, 2, 0);    // 10
        EstadisticaJugadorPartida cuatroMuertes = puntuada(4, 4, 0); // 8
        EstadisticaJugadorPartida seisMuertes = puntuada(6, 6, 0);   // 12

        // Inmortal solo cuenta la partida con menos de 3 muertes
        EvaluadorMejoras.Resultado r = EvaluadorMejoras.evaluar(inmortal, List.of(dosMuertes, cuatroMuertes), NUNCA);
        assertThat(r.cumplida()).isTrue();
        assertThat(r.puntosExtra()).isEqualByComparingTo("5"); // 10 · 0,5
        // Morador: las dos primeras sí (menos de 5), la de 6 muertes no
        r = EvaluadorMejoras.evaluar(morador, List.of(dosMuertes, cuatroMuertes, seisMuertes), NUNCA);
        assertThat(r.puntosExtra()).isEqualByComparingTo("4.5"); // (10 + 8) · 0,25
        assertThat(EvaluadorMejoras.evaluar(inmortal, List.of(seisMuertes), NUNCA).cumplida()).isFalse();
    }

    @Test
    void goodDeathCambiaLoQueValeCadaMuerte() {
        Mejora goodDeath = new Mejora();
        goodDeath.setCondicion(CondicionMejora.SIEMPRE);
        goodDeath.setEfecto(EfectoMejora.PUNTOS_POR_ACCION);
        goodDeath.setEfectoEstadistica(EstadisticaMejora.MUERTES);
        goodDeath.setPuntosPorUnidad(new BigDecimal("-0.5"));
        EvaluadorMejoras.Resultado r = EvaluadorMejoras.evaluar(goodDeath,
                List.of(puntuada(1, 4, 0), puntuada(0, 3, 0)), NUNCA);
        assertThat(r.cumplida()).isTrue();
        assertThat(r.puntosExtra()).isEqualByComparingTo("3.5"); // 7 muertes · 0,5 que dejan de restar
    }

    @Test
    void inventorPideUnCampeonPocoJugado() {
        Mejora inventor = multiplica(CondicionMejora.CAMPEON_POCO_JUGADO, "2");
        inventor.setUmbral(new BigDecimal("3"));
        EstadisticaJugadorPartida raro = puntuada(3, 1, 2);   // 12
        raro.setCampeon("Teemo");
        EstadisticaJugadorPartida meta = puntuada(3, 1, 2);
        meta.setCampeon("Azir");
        EstadisticaJugadorPartida sinDato = puntuada(3, 1, 2);
        java.util.function.ToLongFunction<EstadisticaJugadorPartida> veces = e -> e.getCampeon().equals("Teemo") ? 1 : 40;

        assertThat(EvaluadorMejoras.cumple(inventor, raro, veces)).isTrue();
        assertThat(EvaluadorMejoras.cumple(inventor, meta, veces)).isFalse();
        assertThat(EvaluadorMejoras.cumple(inventor, sinDato, veces)).isFalse(); // sin campeón importado no cuenta
        assertThat(EvaluadorMejoras.evaluar(inventor, List.of(raro, meta), veces).puntosExtra()).isEqualByComparingTo("12");
    }

    @Test
    void reglaLegibleDeCadaMejora() {
        assertThat(com.lecfantasy.dto.ReglaMejora.texto(
                porEstadistica(EstadisticaMejora.MUERTES, ComparadorMejora.MENOR, "3", "1.5")))
                .isEqualTo("Si muertes < 3 en una partida: puntos de esa partida ×1,5");
        Mejora goodDeath = new Mejora();
        goodDeath.setCondicion(CondicionMejora.SIEMPRE);
        goodDeath.setEfecto(EfectoMejora.PUNTOS_POR_ACCION);
        goodDeath.setEfectoEstadistica(EstadisticaMejora.MUERTES);
        goodDeath.setPuntosPorUnidad(new BigDecimal("-0.50"));
        assertThat(com.lecfantasy.dto.ReglaMejora.texto(goodDeath))
                .isEqualTo("En todas sus partidas de la jornada: cada unidad de muertes vale −0,5 (en lugar de −1)");
    }
}
