package com.lecfantasy.service.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.lecfantasy.domain.Posicion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Descarga estadísticas de Leaguepedia (lol.fandom.com) mediante su API Cargo, tabla ScoreboardPlayers.
 * Ventaja: automático. Limitación: no trae dobles/triples/cuádruples/pentas ni first blood;
 * para eso está la importación del CSV de Oracle's Elixir.
 */
@Component
public class LeaguepediaClient {

    private static final Logger log = LoggerFactory.getLogger(LeaguepediaClient.class);
    private static final int PAGINA = 500;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CAMPOS = String.join(",",
            "SP.GameId=GameId", "SP.DateTime_UTC=Fecha", "SP.Team=Team", "SP.Link=Link", "SP.Role=Role",
            "SP.Champion=Champion", "SP.Kills=Kills", "SP.Deaths=Deaths", "SP.Assists=Assists", "SP.CS=CS",
            "SP.VisionScore=VisionScore", "SP.PlayerWin=PlayerWin");

    private final LeaguepediaApi api;

    public LeaguepediaClient(LeaguepediaApi api) {
        this.api = api;
    }

    /**
     * @param overviewPage p. ej. "LEC/2026 Season/Spring Season"; si es null se buscan todas las páginas "LEC/..."
     */
    public List<StatLine> obtener(String overviewPage, LocalDate desde, LocalDate hasta) {
        String filtroTorneo = overviewPage == null || overviewPage.isBlank()
                ? "SP.OverviewPage LIKE \"LEC/%\""
                : "SP.OverviewPage=\"" + overviewPage.replace("\"", "") + "\"";
        String where = filtroTorneo
                + " AND SP.DateTime_UTC >= \"" + desde + " 00:00:00\""
                + " AND SP.DateTime_UTC < \"" + hasta.plusDays(1) + " 00:00:00\"";

        List<StatLine> lineas = new ArrayList<>();
        int offset = 0;
        while (true) {
            JsonNode raiz = api.consultar(Map.of(
                    "action", "cargoquery",
                    "tables", "ScoreboardPlayers=SP",
                    "fields", CAMPOS,
                    "where", where,
                    "order_by", "SP.DateTime_UTC,SP.GameId",
                    "limit", String.valueOf(PAGINA),
                    "offset", String.valueOf(offset)));
            JsonNode filas = raiz.path("cargoquery");
            for (JsonNode fila : filas) {
                StatLine l = convertir(fila.path("title"));
                if (l != null) {
                    lineas.add(l);
                }
            }
            if (filas.size() < PAGINA) {
                break;
            }
            offset += PAGINA; // LeaguepediaApi espera entre consultas (Leaguepedia limita las peticiones por minuto)
        }
        log.info("Leaguepedia: {} líneas de jugador entre {} y {}", lineas.size(), desde, hasta);
        return lineas;
    }

    StatLine convertir(JsonNode t) {
        Posicion posicion = Posicion.desdeTextoExterno(t.path("Role").asText(null));
        String gameId = t.path("GameId").asText("");
        String fechaTexto = t.path("Fecha").asText("");
        String link = t.path("Link").asText("");
        if (posicion == null || gameId.isBlank() || fechaTexto.length() < 19 || link.isBlank()) {
            return null;
        }
        LocalDateTime fecha = LocalDateTime.parse(fechaTexto.substring(0, 19), FECHA);
        return new StatLine(
                "LP:" + gameId,
                fecha,
                t.path("Team").asText(null),
                null,
                nickname(link),
                "LP:" + link,
                posicion,
                t.path("Champion").asText(null),
                Numeros.entero(t.path("Kills").asText()),
                Numeros.entero(t.path("Deaths").asText()),
                Numeros.entero(t.path("Assists").asText()),
                Numeros.entero(t.path("CS").asText()),
                Numeros.entero(t.path("VisionScore").asText()),
                0, 0, 0, 0,
                false,
                Numeros.verdadero(t.path("PlayerWin").asText()),
                null);
    }

    /** "Caps (Rasmus Winther)" → "Caps". */
    static String nickname(String link) {
        int parentesis = link.indexOf(" (");
        return parentesis > 0 ? link.substring(0, parentesis).trim() : link.trim();
    }

}
