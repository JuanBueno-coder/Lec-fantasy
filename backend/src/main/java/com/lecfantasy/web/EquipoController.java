package com.lecfantasy.web;

import com.lecfantasy.dto.EquipoDtos.*;
import com.lecfantasy.security.AuthUser;
import com.lecfantasy.service.EquipoService;
import com.lecfantasy.service.MejoraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipos/{equipoId}")
public class EquipoController {

    private final EquipoService equipos;
    private final MejoraService mejoras;

    public EquipoController(EquipoService equipos, MejoraService mejoras) {
        this.equipos = equipos;
        this.mejoras = mejoras;
    }

    @GetMapping
    public EquipoDto ver(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId) {
        return equipos.ver(yo, equipoId);
    }

    @PostMapping("/fichajes")
    public EquipoDto fichar(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                            @Valid @RequestBody FichajeRequest req) {
        return equipos.fichar(yo, equipoId, req.jugadorId());
    }

    @DeleteMapping("/plantilla/{jugadorId}")
    public EquipoDto vender(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId, @PathVariable Long jugadorId) {
        return equipos.vender(yo, equipoId, jugadorId);
    }

    @PutMapping("/alineacion")
    public EquipoDto alinear(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                             @Valid @RequestBody AlineacionRequest req) {
        return equipos.alinear(yo, equipoId, req);
    }

    @GetMapping("/transacciones")
    public List<TransaccionDto> transacciones(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId) {
        return equipos.transacciones(yo, equipoId);
    }

    @GetMapping("/puntuaciones")
    public List<PuntuacionDto> puntuaciones(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId) {
        return equipos.puntuaciones(yo, equipoId);
    }

    // ---------- Mejoras: inventario del equipo ----------

    /** Inventario (sin asignar), asignadas a la próxima jornada e historial de usadas. */
    @GetMapping("/mejoras")
    public List<MejoraAplicadaDto> mejoras(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId) {
        return mejoras.delEquipo(yo, equipoId);
    }

    /** Compra en el mercado de mejoras. Con jugadorId, además la asigna a ese titular. */
    @PostMapping("/mejoras")
    @ResponseStatus(HttpStatus.CREATED)
    public MejoraAplicadaDto comprarMejora(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                                           @Valid @RequestBody ComprarMejoraRequest req) {
        return mejoras.comprar(yo, equipoId, req);
    }

    /** Asigna una mejora del inventario a un titular para la próxima jornada (o la cambia de jugador). */
    @PutMapping("/mejoras/{mejoraAplicadaId}/jugador")
    public MejoraAplicadaDto asignarMejora(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                                           @PathVariable Long mejoraAplicadaId,
                                           @Valid @RequestBody AsignarMejoraRequest req) {
        return mejoras.asignar(yo, equipoId, mejoraAplicadaId, req.jugadorId());
    }

    /** Quita la mejora al jugador: vuelve al inventario. */
    @DeleteMapping("/mejoras/{mejoraAplicadaId}/jugador")
    public MejoraAplicadaDto quitarMejora(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                                          @PathVariable Long mejoraAplicadaId) {
        return mejoras.quitar(yo, equipoId, mejoraAplicadaId);
    }

    /** Devuelve una mejora sin usar y recupera lo pagado. */
    @DeleteMapping("/mejoras/{mejoraAplicadaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolverMejora(@AuthenticationPrincipal AuthUser yo, @PathVariable Long equipoId,
                               @PathVariable Long mejoraAplicadaId) {
        mejoras.devolver(yo, equipoId, mejoraAplicadaId);
    }
}
