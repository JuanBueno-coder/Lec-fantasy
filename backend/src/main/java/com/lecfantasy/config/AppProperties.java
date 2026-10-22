package com.lecfantasy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Google google,
        Cors cors,
        String frontendUrl,
        Admin admin,
        Juego juego,
        Mail mail,
        Leaguepedia leaguepedia) {

    public record Jwt(String secret, long expiracionHoras, long expiracionRecordarmeDias) {
    }

    /** Client IDs de OAuth de Google (web y Android) que aceptamos como audiencia del ID token. */
    public record Google(List<String> clientIds) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Si se definen, al arrancar se crea este administrador cuando no existe ninguno. */
    public record Admin(String email, String password, String nombre) {
    }

    public record Juego(BigDecimal valorInicialJugador, BigDecimal presupuestoLigaGlobal, int edadMinima) {
    }

    public record Mail(String from) {
    }

    public record Leaguepedia(String url, String userAgent) {
    }
}
