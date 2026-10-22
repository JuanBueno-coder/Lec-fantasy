package com.lecfantasy.service;

import com.lecfantasy.common.ApiException;
import com.lecfantasy.config.AppProperties;
import com.lecfantasy.domain.*;
import com.lecfantasy.dto.LigaDtos.*;
import com.lecfantasy.repository.*;
import com.lecfantasy.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LigaService {

    private final GrupoLigaRepository grupos;
    private final EquipoFantasyRepository equipos;
    private final UsuarioRepository usuarios;
    private final JornadaRepository jornadas;
    private final PuntuacionJornadaRepository puntuaciones;
    private final AppProperties props;

    public LigaService(GrupoLigaRepository grupos, EquipoFantasyRepository equipos, UsuarioRepository usuarios,
                       JornadaRepository jornadas, PuntuacionJornadaRepository puntuaciones, AppProperties props) {
        this.grupos = grupos;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.jornadas = jornadas;
        this.puntuaciones = puntuaciones;
        this.props = props;
    }

    // ------------------------- Liga global -------------------------

    @Transactional
    public GrupoLiga ligaGlobal() {
        return grupos.findFirstByEsGlobalTrue().orElseGet(() -> {
            GrupoLiga g = new GrupoLiga();
            g.setNombre(ReglasJuego.NOMBRE_LIGA_GLOBAL);
            g.setTipo(ReglasJuego.TIPO_LIGA_GLOBAL);
            g.setCodigoInvitacion(codigoUnico());
            g.setPresupuestoInicial(props.juego().presupuestoLigaGlobal());
            g.setMaxParticipantes(ReglasJuego.MAX_PARTICIPANTES_GLOBAL);
            g.setEsGlobal(true);
            g.setFechaCreacion(LocalDateTime.now());
            return grupos.save(g);
        });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<GrupoLiga> ligaGlobalSiExiste() {
        return grupos.findFirstByEsGlobalTrue();
    }

    /** Todo usuario nuevo entra automáticamente en la Liga Global. */
    @Transactional
    public void unirALigaGlobal(Usuario u) {
        GrupoLiga global = ligaGlobal();
        if (!equipos.existsByUsuarioIdAndGrupoId(u.getId(), global.getId())) {
            crearEquipo(u, global, "Equipo de " + u.getNombre());
        }
    }

    // ------------------------- Consultas -------------------------

    @Transactional(readOnly = true)
    public List<LigaDto> publicas(Long usuarioId) {
        Map<Long, Long> misEquipos = misEquiposPorLiga(usuarioId);
        return grupos.findByTipoOrderByFechaCreacionDesc(TipoLiga.PUBLICO).stream()
                .map(g -> LigaDto.of(g, equipos.countByGrupoId(g.getId()), misEquipos.containsKey(g.getId()),
                        misEquipos.get(g.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LigaDto> mias(Long usuarioId) {
        return equipos.findByUsuarioConGrupo(usuarioId).stream()
                .map(e -> LigaDto.of(e.getGrupo(), equipos.countByGrupoId(e.getGrupo().getId()), true, e.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LigaDto detalle(AuthUser yo, Long ligaId) {
        GrupoLiga g = obtener(ligaId);
        Long miEquipo = equipos.findByUsuarioIdAndGrupoId(yo.id(), ligaId).map(EquipoFantasy::getId).orElse(null);
        if (g.getTipo() == TipoLiga.PRIVADO && miEquipo == null && !yo.esAdmin()) {
            throw ApiException.prohibido("Esta liga es privada");
        }
        return LigaDto.of(g, equipos.countByGrupoId(ligaId), miEquipo != null, miEquipo);
    }

    @Transactional(readOnly = true)
    public List<ClasificacionFila> clasificacion(AuthUser yo, Long ligaId, Long jornadaId) {
        detalle(yo, ligaId); // comprueba acceso
        List<ClasificacionFila> filas = new ArrayList<>();
        if (jornadaId == null) {
            List<EquipoFantasy> lista = equipos.clasificacion(ligaId);
            for (int i = 0; i < lista.size(); i++) {
                EquipoFantasy e = lista.get(i);
                filas.add(new ClasificacionFila(i + 1, e.getId(), e.getNombre(), e.getUsuario().getId(),
                        e.getUsuario().getNombre(), e.getPuntosTotales()));
            }
        } else {
            if (!jornadas.existsById(jornadaId)) {
                throw ApiException.noEncontrado("Jornada");
            }
            List<PuntuacionJornada> lista = puntuaciones.clasificacionJornada(ligaId, jornadaId);
            for (int i = 0; i < lista.size(); i++) {
                PuntuacionJornada p = lista.get(i);
                EquipoFantasy e = p.getEquipo();
                filas.add(new ClasificacionFila(i + 1, e.getId(), e.getNombre(), e.getUsuario().getId(),
                        e.getUsuario().getNombre(), p.getPuntosTotales()));
            }
        }
        return filas;
    }

    // ------------------------- Acciones -------------------------

    @Transactional
    public LigaDto crear(AuthUser yo, CrearLigaRequest req) {
        Usuario u = usuarios.findById(yo.id()).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        GrupoLiga g = new GrupoLiga();
        g.setNombre(req.nombre().trim());
        g.setTipo(req.tipo());
        g.setCodigoInvitacion(codigoUnico());
        g.setPresupuestoInicial(req.presupuestoInicial());
        g.setMaxParticipantes(req.maxParticipantes());
        g.setCreador(u);
        g.setFechaCreacion(LocalDateTime.now());
        grupos.save(g);
        EquipoFantasy e = crearEquipo(u, g, req.nombreEquipo().trim());
        return LigaDto.of(g, 1, true, e.getId());
    }

    @Transactional
    public LigaDto unirsePorCodigo(AuthUser yo, UnirsePorCodigoRequest req) {
        GrupoLiga g = grupos.findByCodigoInvitacion(req.codigo().trim().toUpperCase())
                .orElseThrow(() -> ApiException.noEncontrado("Código de invitación"));
        return unirseA(yo, g, req.nombreEquipo());
    }

    @Transactional
    public LigaDto unirse(AuthUser yo, Long ligaId, UnirseRequest req) {
        GrupoLiga g = obtener(ligaId);
        if (g.getTipo() != TipoLiga.PUBLICO) {
            throw ApiException.prohibido("Para entrar en una liga privada necesitas el código de invitación");
        }
        return unirseA(yo, g, req.nombreEquipo());
    }

    @Transactional
    public void abandonar(AuthUser yo, Long ligaId) {
        GrupoLiga g = obtener(ligaId);
        if (g.isEsGlobal()) {
            throw ApiException.conflicto("No puedes abandonar la Liga Global");
        }
        EquipoFantasy e = equipos.findByUsuarioIdAndGrupoId(yo.id(), ligaId)
                .orElseThrow(() -> ApiException.conflicto("No perteneces a esta liga"));
        equipos.delete(e);
    }

    @Transactional
    public void eliminar(AuthUser yo, Long ligaId) {
        GrupoLiga g = obtener(ligaId);
        if (g.isEsGlobal()) {
            throw ApiException.conflicto("La Liga Global no se puede eliminar");
        }
        boolean esCreador = g.getCreador() != null && g.getCreador().getId().equals(yo.id());
        if (!esCreador && !yo.esAdmin()) {
            throw ApiException.prohibido("Solo el creador puede eliminar la liga");
        }
        grupos.delete(g);
    }

    // ------------------------- Auxiliares -------------------------

    private LigaDto unirseA(AuthUser yo, GrupoLiga g, String nombreEquipo) {
        if (equipos.existsByUsuarioIdAndGrupoId(yo.id(), g.getId())) {
            throw ApiException.conflicto("Ya estás en esta liga");
        }
        long participantes = equipos.countByGrupoId(g.getId());
        if (participantes >= g.getMaxParticipantes()) {
            throw ApiException.conflicto("La liga está completa");
        }
        Usuario u = usuarios.findById(yo.id()).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        EquipoFantasy e = crearEquipo(u, g, nombreEquipo.trim());
        return LigaDto.of(g, participantes + 1, true, e.getId());
    }

    private EquipoFantasy crearEquipo(Usuario u, GrupoLiga g, String nombre) {
        EquipoFantasy e = new EquipoFantasy();
        e.setUsuario(u);
        e.setGrupo(g);
        e.setNombre(nombre.length() > 40 ? nombre.substring(0, 40) : nombre);
        e.setDineroDisponible(g.getPresupuestoInicial());
        e.setPuntosTotales(BigDecimal.ZERO);
        e.setFechaUnion(LocalDateTime.now());
        return equipos.save(e);
    }

    private Map<Long, Long> misEquiposPorLiga(Long usuarioId) {
        if (usuarioId == null) {
            return Map.of();
        }
        return equipos.findByUsuarioConGrupo(usuarioId).stream()
                .collect(Collectors.toMap(e -> e.getGrupo().getId(), EquipoFantasy::getId, (a, b) -> a));
    }

    private GrupoLiga obtener(Long id) {
        return grupos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Liga"));
    }

    private String codigoUnico() {
        String codigo;
        do {
            codigo = CodigoGenerator.codigo(8);
        } while (grupos.existsByCodigoInvitacion(codigo));
        return codigo;
    }
}
