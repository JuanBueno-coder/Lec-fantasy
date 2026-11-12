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
        Leaguepedia leaguepedia,
        Automatizacion automatizacion,
        PandaScore pandascore,
        Noticias noticias) {

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

    /**
     * API de Leaguepedia (lol.fandom.com). fotosAutomaticas: si la automatización semanal trae también
     * las fotos de los jugadores que aún no tienen (el botón del panel funciona siempre).
     * usuario/password: bot password de Fandom (Special:BotPasswords), opcional; sin él Leaguepedia solo
     * deja una consulta por minuto aproximadamente.
     */
    public record Leaguepedia(String url, String userAgent, boolean fotosAutomaticas, String usuario,
                              String password) {
    }

    /**
     * Gestión automática de jornadas con datos de Leaguepedia.
     * habilitada: activa las tareas programadas (el botón "Ejecutar ahora" del admin funciona siempre).
     * cronInicio: cuándo se comprueba si hay que iniciar una jornada (cierra el mercado el día de inicio).
     * cronImportacion: cuándo se importan y cierran las jornadas ya terminadas (por defecto, los lunes).
     * finalizar: si es false solo importa y el admin revisa los puntos antes de cerrar a mano.
     */
    public record Automatizacion(boolean habilitada, String zona, String cronInicio, String cronImportacion,
                                 String cronCalendario, boolean finalizar) {
    }

    /**
     * API de PandaScore (calendario y resultados). token vacío = desactivado.
     * ligaId: id de la LEC en PandaScore; si no se indica se busca por ligaNombre.
     * diasCalendario: cuántos días hacia delante se miran para crear jornadas y mostrar partidos.
     */
    public record PandaScore(String url, String token, Long ligaId, String ligaNombre, int diasCalendario) {
    }

    /**
     * Noticias importadas de los canales oficiales de la LEC.
     * automaticas: si la tarea programada (cron) las trae sola; el botón del panel funciona siempre.
     */
    public record Noticias(boolean automaticas, String cron, X x, YouTube youtube) {
    }

    /**
     * API v2 de X (Twitter). token vacío = desactivado. Es de pago por uso (cada publicación leída cuenta),
     * así que solo se piden las publicaciones nuevas desde la última importada (since_id).
     * cuentas: usuarios sin @, p. ej. LEC. maxPorConsulta: entre 5 y 100.
     */
    public record X(String url, String token, List<String> cuentas, int maxPorConsulta) {
    }

    /**
     * Vídeos de canales de YouTube por su feed RSS público (gratis, sin key).
     * canales: id del canal (UC...) o su @usuario, que se resuelve al id la primera vez.
     */
    public record YouTube(List<String> canales) {
    }
}
