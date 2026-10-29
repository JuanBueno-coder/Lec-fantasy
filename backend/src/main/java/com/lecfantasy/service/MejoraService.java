package com.lecfantasy.service;

import com.lecfantasy.common.ApiException;
import com.lecfantasy.domain.*;
import com.lecfantasy.dto.CatalogoDtos.MejoraDto;
import com.lecfantasy.dto.CatalogoDtos.MejoraRequest;
import com.lecfantasy.dto.EquipoDtos.ComprarMejoraRequest;
import com.lecfantasy.dto.EquipoDtos.MejoraAplicadaDto;
import com.lecfantasy.repository.*;
import com.lecfantasy.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mercado de mejoras e inventario.
 * <ul>
 *   <li>Se compran en el mercado de mejoras y van al inventario del equipo (uno por liga).</li>
 *   <li>Desde el inventario se asignan a un titular para la próxima jornada programada.
 *       Cada titular lleva como mucho una mejora por jornada (también puede no llevar ninguna).</li>
 *   <li>Cada mejora tiene un solo uso: vale para todas las partidas de esa jornada y se gasta al cerrarla.</li>
 *   <li>Con el mercado abierto se puede quitar (vuelve al inventario) o devolver (se recupera lo pagado).</li>
 * </ul>
 */
@Service
public class MejoraService {

    /** Estados en los que la mejora cuenta para el límite por equipo (aún sin usar). */
    private static final Set<EstadoMejora> SIN_USAR = EnumSet.of(EstadoMejora.INVENTARIO, EstadoMejora.PENDIENTE);

    private final MejoraRepository mejoras;
    private final MejoraAplicadaRepository aplicadas;
    private final JornadaRepository jornadas;
    private final PlantillaJugadorRepository plantillas;
    private final EquipoService equipoService;
    private final ActividadService actividad;

    public MejoraService(MejoraRepository mejoras, MejoraAplicadaRepository aplicadas, JornadaRepository jornadas,
                         PlantillaJugadorRepository plantillas, EquipoService equipoService,
                         ActividadService actividad) {
        this.mejoras = mejoras;
        this.aplicadas = aplicadas;
        this.jornadas = jornadas;
        this.plantillas = plantillas;
        this.equipoService = equipoService;
        this.actividad = actividad;
    }

    @Transactional(readOnly = true)
    public List<MejoraDto> catalogo(boolean incluirInactivas) {
        return (incluirInactivas ? mejoras.findAllByOrderByIdAsc() : mejoras.findByActivaTrueOrderByPrecioAsc())
                .stream().map(MejoraDto::of).toList();
    }

    /** Inventario primero, luego las asignadas y por último el historial de usadas. */
    @Transactional(readOnly = true)
    public List<MejoraAplicadaDto> delEquipo(AuthUser yo, Long equipoId) {
        equipoService.visible(yo, equipoId);
        return aplicadas.findByEquipo(equipoId).stream()
                .sorted(Comparator.comparingInt((MejoraAplicada m) -> m.getEstado().ordinal() > 1 ? 2 : m.getEstado().ordinal()))
                .map(MejoraAplicadaDto::of).toList();
    }

    /** Compra una mejora para el inventario (y, si se indica jugador, la asigna en el mismo paso). */
    @Transactional
    public MejoraAplicadaDto comprar(AuthUser yo, Long equipoId, ComprarMejoraRequest req) {
        EquipoFantasy equipo = equipoService.propio(yo, equipoId);
        equipoService.comprobarMercadoAbierto();
        Mejora mejora = mejoras.findById(req.mejoraId()).filter(Mejora::isActiva)
                .orElseThrow(() -> ApiException.noEncontrado("Mejora"));
        if (mejora.getMaxPorEquipo() != null
                && aplicadas.countByEquipoIdAndMejoraIdAndEstadoIn(equipoId, mejora.getId(), SIN_USAR) >= mejora.getMaxPorEquipo()) {
            throw ApiException.conflicto(mejora.getMaxPorEquipo() == 1
                    ? "Ya tienes un " + mejora.getNombre() + " sin usar: solo se puede tener uno"
                    : "Ya tienes " + mejora.getMaxPorEquipo() + " mejoras «" + mejora.getNombre() + "» sin usar, que es el máximo");
        }
        if (equipo.getDineroDisponible().compareTo(mejora.getPrecio()) < 0) {
            throw ApiException.conflicto("No tienes dinero suficiente para esta mejora");
        }
        equipo.setDineroDisponible(equipo.getDineroDisponible().subtract(mejora.getPrecio()));
        MejoraAplicada m = new MejoraAplicada();
        m.setEquipo(equipo);
        m.setMejora(mejora);
        m.setPrecioPagado(mejora.getPrecio());
        m.setFechaCompra(LocalDateTime.now());
        aplicadas.save(m);
        if (req.jugadorId() != null) {
            asignarA(m, equipoId, req.jugadorId());
        }
        return MejoraAplicadaDto.of(m);
    }

    /** Asigna (o cambia de jugador) una mejora sin usar a un titular para la próxima jornada. */
    @Transactional
    public MejoraAplicadaDto asignar(AuthUser yo, Long equipoId, Long mejoraAplicadaId, Long jugadorId) {
        equipoService.propio(yo, equipoId);
        equipoService.comprobarMercadoAbierto();
        MejoraAplicada m = delEquipo(equipoId, mejoraAplicadaId);
        if (!SIN_USAR.contains(m.getEstado())) {
            throw ApiException.conflicto("Esta mejora ya se ha usado");
        }
        asignarA(m, equipoId, jugadorId);
        return MejoraAplicadaDto.of(m);
    }

    /** Quita la mejora del jugador y la devuelve al inventario. */
    @Transactional
    public MejoraAplicadaDto quitar(AuthUser yo, Long equipoId, Long mejoraAplicadaId) {
        equipoService.propio(yo, equipoId);
        equipoService.comprobarMercadoAbierto();
        MejoraAplicada m = delEquipo(equipoId, mejoraAplicadaId);
        if (m.getEstado() != EstadoMejora.PENDIENTE) {
            throw ApiException.conflicto("Esta mejora no está asignada a ningún jugador");
        }
        m.devolverAlInventario();
        return MejoraAplicadaDto.of(m);
    }

    /** Devuelve una mejora sin usar al mercado y recupera lo pagado (solo con el mercado abierto). */
    @Transactional
    public void devolver(AuthUser yo, Long equipoId, Long mejoraAplicadaId) {
        EquipoFantasy equipo = equipoService.propio(yo, equipoId);
        equipoService.comprobarMercadoAbierto();
        MejoraAplicada m = delEquipo(equipoId, mejoraAplicadaId);
        if (!SIN_USAR.contains(m.getEstado())
                || (m.getJornada() != null && m.getJornada().getEstado() != EstadoJornada.PROGRAMADA)) {
            throw ApiException.conflicto("Esta mejora ya no se puede devolver");
        }
        equipo.setDineroDisponible(equipo.getDineroDisponible().add(m.getPrecioPagado()));
        aplicadas.delete(m);
    }

    private MejoraAplicada delEquipo(Long equipoId, Long mejoraAplicadaId) {
        return aplicadas.findById(mejoraAplicadaId)
                .filter(x -> x.getEquipo().getId().equals(equipoId))
                .orElseThrow(() -> ApiException.noEncontrado("Mejora"));
    }

    /** Un titular, la próxima jornada programada y como mucho una mejora por jugador en ella. */
    private void asignarA(MejoraAplicada m, Long equipoId, Long jugadorId) {
        Jornada jornada = jornadas.findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.PROGRAMADA)
                .orElseThrow(() -> ApiException.conflicto("No hay ninguna jornada programada a la que aplicar la mejora"));
        PlantillaJugador p = plantillas.findByEquipoIdAndJugadorId(equipoId, jugadorId)
                .orElseThrow(() -> ApiException.conflicto("Ese jugador no está en tu plantilla"));
        String nick = p.getJugador().getNickname();
        if (!p.getSlot().esTitular()) {
            throw ApiException.conflicto(nick + " está en el banquillo: las mejoras solo se pueden dar a los titulares");
        }
        boolean mismoJugador = m.getJugador() != null && m.getJugador().getId().equals(jugadorId)
                && m.getJornada() != null && m.getJornada().getId().equals(jornada.getId());
        if (!mismoJugador && aplicadas.existsByEquipoIdAndJugadorIdAndJornadaId(equipoId, jugadorId, jornada.getId())) {
            throw ApiException.conflicto(nick + " ya tiene una mejora para " + jornada.getNombre()
                    + ": cada jugador solo puede llevar una");
        }
        m.asignar(p.getJugador(), jornada);
        aplicadas.flush(); // la restricción única de la BD es la última barrera
    }

    // ------------------------- Administración -------------------------

    @Transactional
    public MejoraDto crear(AuthUser admin, MejoraRequest req) {
        Mejora m = new Mejora();
        aplicar(m, req);
        mejoras.save(m);
        actividad.registrar(admin, TipoActividad.MEJORA, admin.nombre() + " creó la mejora «" + m.getNombre() + "»");
        return MejoraDto.of(m);
    }

    @Transactional
    public MejoraDto editar(AuthUser admin, Long id, MejoraRequest req) {
        Mejora m = mejoras.findById(id).orElseThrow(() -> ApiException.noEncontrado("Mejora"));
        aplicar(m, req);
        actividad.registrar(admin, TipoActividad.MEJORA, admin.nombre() + " editó la mejora «" + m.getNombre() + "»");
        return MejoraDto.of(m);
    }

    /** Copia el formulario a la mejora comprobando que estén los campos que usa cada tipo. */
    static void aplicar(Mejora m, MejoraRequest req) {
        CondicionMejora condicion = req.condicion();
        if (condicion == CondicionMejora.ESTADISTICA && (req.estadistica() == null || req.comparador() == null)) {
            throw ApiException.peticionInvalida("Indica la estadística y el comparador de la condición");
        }
        if (condicion.necesitaUmbral() && req.umbral() == null) {
            throw ApiException.peticionInvalida("Esta condición necesita un umbral");
        }
        if (condicion == CondicionMejora.CAMPEON_POCO_JUGADO && req.umbral().signum() < 0) {
            throw ApiException.peticionInvalida("El umbral de veces elegido no puede ser negativo");
        }
        if (req.efecto() == EfectoMejora.MULTIPLICADOR) {
            if (req.multiplicador() == null || req.multiplicador().compareTo(BigDecimal.ONE) == 0) {
                throw ApiException.peticionInvalida("Indica un multiplicador distinto de 1 (p. ej. 1,25 o 2)");
            }
        } else {
            if (req.efectoEstadistica() == null || !req.efectoEstadistica().admiteEfecto()) {
                throw ApiException.peticionInvalida("Elige la estadística cuyo valor cambia (kills, muertes, asistencias...)");
            }
            if (req.puntosPorUnidad() == null) {
                throw ApiException.peticionInvalida("Indica cuántos puntos vale cada unidad");
            }
        }
        boolean porEstadistica = condicion == CondicionMejora.ESTADISTICA;
        boolean porAccion = req.efecto() == EfectoMejora.PUNTOS_POR_ACCION;
        m.setNombre(req.nombre().trim());
        m.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        m.setPrecio(req.precio().setScale(2, RoundingMode.HALF_UP));
        m.setCondicion(condicion);
        m.setEstadistica(porEstadistica ? req.estadistica() : null);
        m.setComparador(porEstadistica ? req.comparador() : null);
        m.setUmbral(condicion.necesitaUmbral() ? req.umbral().setScale(2, RoundingMode.HALF_UP) : null);
        m.setEfecto(req.efecto());
        m.setMultiplicador(porAccion ? BigDecimal.ONE : req.multiplicador().setScale(2, RoundingMode.HALF_UP));
        m.setEfectoEstadistica(porAccion ? req.efectoEstadistica() : null);
        m.setPuntosPorUnidad(porAccion ? req.puntosPorUnidad().setScale(2, RoundingMode.HALF_UP) : null);
        m.setMaxPorEquipo(req.maxPorEquipo());
        m.setActiva(req.activa());
    }
}
