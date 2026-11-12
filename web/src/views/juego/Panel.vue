<!--
  Pantalla de inicio del juego: «La semana en la LEC».
  Lo que pasa en la competición, igual para todas tus ligas: partidos de la jornada (con cuántos
  jugadores tuyos juegan contando todos tus equipos), jugador de la jornada, top 5, los que más
  suben y bajan de precio y las noticias de los canales oficiales.
-->
<template>
  <div>
    <Alerta v-if="route.query.bienvenida" tipo="ok" class="mb"
            :mensaje="`¡Bienvenido, ${auth.usuario?.nombre}! Ya estás en la Liga Global. Empieza fichando a tus cinco titulares.`" />
    <Alerta :mensaje="error" class="mb" />

    <header class="cabeza">
      <div class="cabeza-texto">
        <span class="kicker">Hola, {{ auth.usuario?.nombre }} · La semana en la LEC</span>
        <h1>{{ jornadaActual?.nombre || 'La semana en la LEC' }}</h1>
        <p class="muted sin-margen">Lo que pasa en la competición, igual para todas tus ligas.</p>
      </div>
      <!-- En el móvil la barra superior no tiene la cuenta atrás: se muestra aquí -->
      <div class="cierre">
        <template v-if="juego.enCurso">
          <span class="micro">Jornada en curso</span>
          <span class="cierre-valor">Mercado cerrado</span>
          <span class="tenue">Hasta el {{ fecha(juego.enCurso.fechaFin) }}</span>
        </template>
        <template v-else-if="juego.proxima">
          <span class="micro">Cierre de alineación</span>
          <span class="cierre-valor texto-acento"><CuentaAtras :hasta="juego.proxima.fechaInicio" /></span>
          <span class="tenue">{{ juego.proxima.nombre }}</span>
        </template>
        <template v-else>
          <span class="micro">Mercado abierto</span>
          <span class="cierre-valor">Sin jornadas</span>
          <span class="tenue">Aprovecha para completar tus plantillas</span>
        </template>
        <RouterLink to="/ligas" class="btn btn-borde btn-peque">Mis ligas</RouterLink>
      </div>
    </header>

    <div class="semana">
      <div class="principal">
        <!-- Partidos de la jornada -->
        <section class="tarjeta">
          <div class="tarjeta-cabeza">
            <h2>{{ tituloPartidos }}</h2>
            <RouterLink to="/jornadas" class="enlace-acento">Calendario completo →</RouterLink>
          </div>

          <div v-if="cargando" class="cargando">Cargando…</div>
          <!-- Calendario real de la LEC (PandaScore), si el servidor tiene la key -->
          <template v-else-if="calendario.length">
            <div v-for="p in calendario" :key="'c' + p.id" class="partido" :class="{ vivo: p.estado === 'running' }">
              <span class="tenue hora">
                <span v-if="p.estado === 'running'" class="chip chip-solido">En directo</span>
                <template v-else>{{ fechaHora(p.fecha) }}</template>
              </span>
              <span class="lado" :class="{ gana: p.ganadorId && p.ganadorId === p.equipoA.id }">
                <InsigniaEquipo :equipo="comoEquipo(p.equipoA)" nombre="siglas" />
              </span>
              <span class="vs">
                <template v-if="p.marcadorA !== null && p.estado !== 'not_started'">{{ p.marcadorA }} – {{ p.marcadorB }}</template>
                <template v-else>{{ p.mejorDe > 1 ? `Bo${p.mejorDe}` : 'vs' }}</template>
              </span>
              <span class="lado der" :class="{ gana: p.ganadorId && p.ganadorId === p.equipoB.id }">
                <InsigniaEquipo :equipo="comoEquipo(p.equipoB)" nombre="siglas" />
              </span>
              <span class="mios">
                <template v-if="misEnCalendario(p)">
                  <Icono nombre="estrella" :tam="13" /> {{ textoTuyos(misEnCalendario(p)) }}
                </template>
              </span>
            </div>
          </template>
          <div v-else-if="!partidas.length" class="vacio">
            {{ jornadaPartidos ? 'Las partidas aparecerán cuando se importen los resultados.' : 'Todavía no hay jornadas en el calendario.' }}
          </div>
          <RouterLink v-for="p in calendario.length ? [] : ultimosResultados" :key="p.id" :to="`/partidas/${p.id}`" class="partido">
            <span class="tenue hora">{{ fechaHora(p.fechaHora) }}</span>
            <span class="lado" :class="{ gana: p.ganador?.id === p.equipoAzul?.id }">
              <InsigniaEquipo :equipo="p.equipoAzul" nombre="siglas" />
            </span>
            <span class="vs">vs</span>
            <span class="lado der" :class="{ gana: p.ganador?.id === p.equipoRojo?.id }">
              <InsigniaEquipo :equipo="p.equipoRojo" nombre="siglas" />
            </span>
            <span class="mios">
              <template v-if="misJugadoresEn(p)">
                <Icono nombre="estrella" :tam="13" /> {{ textoTuyos(misJugadoresEn(p)) }}
              </template>
            </span>
          </RouterLink>
          <p v-if="misJugadores.size" class="tenue nota">«Tuyos» cuenta tus jugadores de todas tus ligas.</p>
        </section>

        <!-- Precios -->
        <div class="precios">
          <section v-for="bloque in bloquesPrecio" :key="bloque.titulo" class="tarjeta">
            <div class="tarjeta-cabeza">
              <h2>{{ bloque.titulo }}</h2>
              <span class="tenue">último cierre</span>
            </div>
            <RouterLink v-for="v in bloque.lista" :key="v.jugador.id" :to="`/jugadores/${v.jugador.id}`" class="fila-jugador">
              <AvatarJugador :jugador="v.jugador" />
              <span class="nombre-jugador">
                <b>{{ v.jugador.nickname }}</b>
                <span class="tenue">{{ siglasEquipo(v.jugador.equipo) }} · {{ millones(v.jugador.valorMercado) }}</span>
              </span>
              <span :class="bloque.clase">{{ bloque.signo }} {{ decimal(Math.abs(v.porcentaje)) }} %</span>
            </RouterLink>
            <p v-if="!bloque.lista.length" class="vacio peque">{{ bloque.vacio }}</p>
          </section>
        </div>
      </div>

      <aside class="lateral">
        <!-- Jugador de la jornada -->
        <section class="tarjeta destacado">
          <span class="micro">Jugador de la jornada</span>
          <template v-if="mejor">
            <RouterLink :to="`/jugadores/${mejor.jugador.id}`" class="foto-mejor" :aria-label="`Ficha de ${mejor.jugador.nickname}`">
              <img v-if="fotoMejor" :src="fotoMejor" alt="" referrerpolicy="no-referrer" @error="falloFoto = true" />
              <span v-else class="foto-iniciales">{{ iniciales(mejor.jugador.nickname) }}</span>
              <span class="chip chip-solido rol">{{ mejor.jugador.posicion }}</span>
            </RouterLink>
            <div class="mejor-datos">
              <div>
                <h2 class="sin-margen">{{ mejor.jugador.nickname }}</h2>
                <span class="tenue">
                  {{ mejor.jugador.equipo?.nombre || 'Sin equipo' }} · {{ textoPartidas(mejor.partidas) }}
                  <template v-if="misJugadores.has(mejor.jugador.id)"> · <span class="texto-acento">tuyo</span></template>
                </span>
              </div>
              <span class="cifra texto-acento">{{ puntos(mejor.puntos) }}<small> pts</small></span>
            </div>
          </template>
          <p v-else class="vacio peque">Cuando se importen las partidas de la jornada verás aquí al mejor jugador.</p>
        </section>

        <!-- Top 5 -->
        <section class="tarjeta">
          <div class="tarjeta-cabeza"><h2>Top 5 de la jornada</h2></div>
          <RouterLink v-for="(t, i) in semana.top" :key="t.jugador.id" :to="`/jugadores/${t.jugador.id}`" class="fila-jugador">
            <span class="puesto">{{ i + 1 }}</span>
            <AvatarJugador :jugador="t.jugador" />
            <span class="nombre-jugador">
              <b>{{ t.jugador.nickname }}<Icono v-if="misJugadores.has(t.jugador.id)" nombre="estrella" :tam="12" class="mio" /></b>
              <span class="tenue">{{ siglasEquipo(t.jugador.equipo) }}</span>
            </span>
            <span class="pos">{{ t.jugador.posicion }}</span>
            <b class="pts">{{ puntos(t.puntos) }}</b>
          </RouterLink>
          <p v-if="!semana.top.length" class="vacio peque">Aún no hay partidas puntuadas.</p>
        </section>
      </aside>
    </div>

    <!-- Noticias de los canales oficiales -->
    <section v-if="noticias.length" class="noticias">
      <div class="fila-entre mb">
        <h2 class="sin-margen">Noticias</h2>
        <RouterLink to="/noticias" class="enlace-acento">Ver todas →</RouterLink>
      </div>
      <div class="grid grid-3">
        <RouterLink v-for="n in noticias" :key="n.id" :to="`/noticias/${n.id}`" class="tarjeta noticia">
          <span class="noticia-imagen" :class="origenNoticia(n).clase">
            <img v-if="n.imagenUrl && !imagenesRotas.has(n.id)" :src="urlRecurso(n.imagenUrl)" alt="" loading="lazy"
                 referrerpolicy="no-referrer" @error="imagenesRotas.add(n.id)" />
            <span v-else class="noticia-sin-imagen"><Icono nombre="noticias" :tam="28" /></span>
            <span class="chip origen">{{ origenNoticia(n).nombre }}</span>
          </span>
          <span class="tenue">{{ hace(n.fechaPublicacion) }}<template v-if="n.autor"> · {{ n.autor }}</template></span>
          <b class="noticia-titulo">{{ n.titulo }}</b>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Alerta from '../../components/Alerta.vue'
import AvatarJugador from '../../components/AvatarJugador.vue'
import CuentaAtras from '../../components/CuentaAtras.vue'
import Icono from '../../components/Icono.vue'
import InsigniaEquipo from '../../components/InsigniaEquipo.vue'
import { api, urlRecurso } from '../../api/cliente'
import { useAuth } from '../../stores/auth'
import { useJuego } from '../../stores/juego'
import { decimal, fecha, fechaHora, hace, iniciales, millones, puntos } from '../../utils/formato'
import { siglasEquipo } from '../../utils/equipos'
import { origenNoticia } from '../../utils/noticias'

const auth = useAuth()
const juego = useJuego()
const route = useRoute()
const noticias = ref([])
const partidas = ref([])
const calendario = ref([])
const semana = ref({ jornada: null, top: [], suben: [], bajan: [] })
const cargando = ref(true)
const error = ref('')
const falloFoto = ref(false)
const imagenesRotas = reactive(new Set())

// El título es la jornada de la que hablan los datos: la que se juega o la última con partidas
const jornadaActual = computed(() => juego.enCurso || semana.value.jornada || juego.ultimaFinalizada || juego.proxima)
// Partidos: la jornada en juego o, si no hay, los últimos resultados
const jornadaPartidos = computed(() => juego.enCurso || juego.ultimaFinalizada)
// Solo los 4 últimos resultados, del más reciente al más antiguo
const MAX_RESULTADOS = 4
const ultimosResultados = computed(() => [...partidas.value]
  .sort((a, b) => String(b.fechaHora).localeCompare(String(a.fechaHora)))
  .slice(0, MAX_RESULTADOS))
const tituloPartidos = computed(() => {
  if (calendario.value.length) return 'Próximos partidos'
  if (!jornadaPartidos.value) return 'Partidos'
  return juego.enCurso ? 'Últimos partidos de la jornada' : 'Últimos resultados'
})

// Mis jugadores en TODAS mis ligas (sin repetir: el mismo jugador en dos ligas cuenta una vez)
const misPlantillas = computed(() => Object.values(juego.equipos).flatMap((e) => e.plantilla || []).map((p) => p.jugador))
const misJugadores = computed(() => new Set(misPlantillas.value.map((j) => j.id)))
const contarPor = (clave) => {
  const vistos = new Set()
  const mapa = {}
  misPlantillas.value.forEach((j) => {
    if (vistos.has(j.id)) return
    vistos.add(j.id)
    const k = clave(j)
    if (k) mapa[k] = (mapa[k] || 0) + 1
  })
  return mapa
}
const misPorEquipo = computed(() => contarPor((j) => j.equipo?.id))
// En el calendario los equipos vienen de PandaScore: se cruzan por siglas
const misPorSiglas = computed(() => contarPor((j) => siglasEquipo(j.equipo)))
const comoEquipo = (e) => ({ nombre: e.nombre, siglas: e.siglas, logoUrl: e.logoUrl })
const misEnCalendario = (p) => (misPorSiglas.value[siglasEquipo(comoEquipo(p.equipoA))] || 0) +
  (misPorSiglas.value[siglasEquipo(comoEquipo(p.equipoB))] || 0)
const misJugadoresEn = (p) => (misPorEquipo.value[p.equipoAzul?.id] || 0) + (misPorEquipo.value[p.equipoRojo?.id] || 0)
const textoTuyos = (n) => `${n} ${n === 1 ? 'tuyo' : 'tuyos'}`
const textoPartidas = (n) => `${n} ${n === 1 ? 'partida' : 'partidas'}`

const mejor = computed(() => semana.value.top[0] || null)
const fotoMejor = computed(() => (falloFoto.value ? null : urlRecurso(mejor.value?.jugador.fotoUrl)))
const bloquesPrecio = computed(() => [
  { titulo: 'Suben', lista: semana.value.suben, clase: 'sube', signo: '▲', vacio: 'Nadie ha subido de precio todavía.' },
  { titulo: 'Bajan', lista: semana.value.bajan, clase: 'baja', signo: '▼', vacio: 'Nadie ha bajado de precio todavía.' }
])

async function cargarPartidas() {
  partidas.value = jornadaPartidos.value ? await api.get(`/jornadas/${jornadaPartidos.value.id}/partidas`) : []
}

onMounted(async () => {
  try {
    const [ns, cal, sem] = await Promise.all([
      api.get('/noticias', { tamano: 3 }).catch(() => ({ contenido: [] })),
      api.get('/calendario').catch(() => []),
      api.get('/semana').catch(() => null),
      juego.cargar(true)
    ])
    noticias.value = ns.contenido
    if (sem) semana.value = sem
    // Partidos de hoy en adelante (y los que se están jugando), máximo 8
    calendario.value = cal.filter((p) => p.estado === 'running' || p.estado === 'not_started' || p.estado === 'postponed').slice(0, 8)
    await cargarPartidas()
  } catch (e) {
    error.value = e.message
  } finally {
    cargando.value = false
  }
})
watch(() => mejor.value?.jugador.id, () => { falloFoto.value = false })
</script>

<style scoped>
.cabeza { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; flex-wrap: wrap; margin-bottom: 22px; }
.cabeza-texto { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.cabeza h1 { margin: 0; font-size: clamp(1.7rem, 3.6vw, 2.6rem); }
.cierre {
  display: none; grid-template-columns: auto auto; grid-template-rows: auto auto auto; column-gap: 18px; row-gap: 2px; align-items: center;
  padding: 14px 18px; border-radius: var(--radio); background: var(--tarjeta); border: 1px solid var(--borde);
}
.cierre .micro, .cierre-valor, .cierre .tenue { grid-column: 1; }
.cierre .btn { grid-column: 2; grid-row: 1 / 4; }
.cierre-valor { font-family: var(--fuente-titulo); font-weight: 800; font-size: 1.35rem; line-height: 1.15; }

.semana { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px; align-items: start; }
.principal, .lateral { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.precios { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
@media (max-width: 1100px) { .semana { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 640px) { .precios { grid-template-columns: minmax(0, 1fr); } }

.enlace-acento { font-size: 0.72rem; font-weight: 700; letter-spacing: 0.06em; white-space: nowrap; }
.nota { margin: 12px 0 0; }
.vacio.peque { padding: 18px 8px; font-size: 0.86rem; margin: 0; }

/* Partidos */
.partido {
  display: grid; grid-template-columns: 120px 1fr 32px 1fr 110px; align-items: center; gap: 10px;
  padding: 11px 4px; border-top: 1px solid var(--borde); color: var(--texto); font-weight: 700;
}
.partido:hover { text-decoration: none; background: var(--tarjeta-2); }
.lado { color: var(--texto-2); }
.lado.der { justify-self: end; }
.lado.der :deep(.insignia-equipo) { flex-direction: row-reverse; }
.lado.gana { color: var(--texto); }
.lado.gana :deep(.nombre)::after { content: ' ✓'; color: var(--acento-tinta); }
.vs { text-align: center; color: var(--texto-3); font-size: 0.72rem; font-weight: 600; }
.mios { display: flex; align-items: center; gap: 5px; justify-content: flex-end; color: var(--acento-tinta); font-size: 0.74rem; font-weight: 700; }
.hora { font-weight: 500; }
.partido.vivo { background: var(--acento-suave); }

/* Filas de jugador (precios y top 5) */
.fila-jugador {
  display: flex; align-items: center; gap: 12px; padding: 9px 4px; border-top: 1px solid var(--borde); color: var(--texto);
}
.fila-jugador:hover { text-decoration: none; background: var(--tarjeta-2); }
.nombre-jugador { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.25; }
.nombre-jugador b { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.nombre-jugador .tenue { font-size: 0.76rem; }
.mio { color: var(--acento-tinta); }
.puesto { width: 16px; font-family: var(--fuente-titulo); font-weight: 800; color: var(--texto-3); text-align: center; }
.pts { min-width: 48px; text-align: right; font-variant-numeric: tabular-nums; }

/* Jugador de la jornada */
.destacado { display: flex; flex-direction: column; gap: 12px; }
.foto-mejor {
  position: relative; display: block; aspect-ratio: 4 / 3; border-radius: var(--radio-peque); overflow: hidden;
  background: radial-gradient(circle at 50% 35%, var(--acento-medio), transparent 70%), var(--tarjeta-2);
}
.foto-mejor img { width: 100%; height: 100%; object-fit: cover; object-position: top; display: block; }
.foto-iniciales { position: absolute; inset: 0; display: grid; place-items: center; font-family: var(--fuente-titulo); font-weight: 800; font-size: 3rem; color: var(--texto-2); }
.foto-mejor .rol { position: absolute; left: 10px; top: 10px; }
.mejor-datos { display: flex; justify-content: space-between; align-items: flex-end; gap: 12px; }
.mejor-datos h2 { font-size: 1.3rem; }
.cifra small { font-family: var(--fuente); font-size: 0.8rem; color: var(--texto-3); }

/* Noticias */
.noticias { margin-top: 24px; }
.noticias h2 { font-size: 1.15rem; }
.noticia { display: flex; flex-direction: column; gap: 8px; padding: 12px; color: var(--texto); transition: border-color 0.2s, transform 0.2s; }
.noticia:hover { text-decoration: none; border-color: var(--acento-borde); transform: translateY(-2px); }
.noticia-imagen { position: relative; display: block; aspect-ratio: 16 / 9; border-radius: var(--radio-peque); overflow: hidden; background: var(--tarjeta-2); margin-bottom: 4px; }
.noticia-imagen img { width: 100%; height: 100%; object-fit: cover; display: block; }
.noticia-sin-imagen { position: absolute; inset: 0; display: grid; place-items: center; color: var(--texto-3); }
.noticia-imagen .origen { position: absolute; left: 8px; top: 8px; background: rgba(0, 0, 0, 0.72); color: #fff; }
.origen-youtube .origen { background: #c4161c; }
.noticia-titulo { display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.35; }

@media (max-width: 760px) {
  .partido { grid-template-columns: 1fr 26px 1fr; row-gap: 4px; }
  .hora { grid-column: 1 / -1; font-size: 0.74rem; }
  .mios { grid-column: 1 / -1; justify-content: flex-start; }
  .mios:empty { display: none; }
  .cierre { display: grid; width: 100%; }
}
</style>
