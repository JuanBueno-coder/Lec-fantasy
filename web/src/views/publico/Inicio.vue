<!--
  Página de inicio: una sola página de scroll continuo (sin barra de navegación).
  Recorrido: portada → tú eres el manager (objetivos) → cómo funciona (pasos con foto fija)
  → reglas y puntos → comunidad → noticias → llamada final.
  Las secciones aparecen al hacer scroll (v-revelar) y las fotos grandes llevan un paralaje
  suave; las dos cosas se desactivan si el sistema pide reducir el movimiento.
-->
<template>
  <div class="inicio">
    <!-- ===================== PORTADA ===================== -->
    <section class="hero">
      <div class="hero-fondo" data-paralaje="0.35">
        <img :src="fotos.arena" alt="" fetchpriority="high" />
      </div>
      <div class="hero-velo" aria-hidden="true"></div>

      <div class="contenedor hero-contenido">
        <p class="kicker entra" style="--d: 0.1s">El fantasy de la LEC</p>
        <h1 class="hero-titulo entra" style="--d: 0.2s">
          Aquí tú eres<br /><span class="texto-acento">el manager</span>
        </h1>
        <p class="hero-lead entra" style="--d: 0.35s">
          Ficha a las estrellas de la LEC, decide quién sale a la Grieta y suma puntos con cada partida real.
          El estadio grita; tú ya has hecho la alineación.
        </p>
        <div class="fila entra" style="--d: 0.5s">
          <RouterLink v-if="auth.autenticado" :to="rutaInicial(auth)" class="btn btn-grande">Ir a mi panel</RouterLink>
          <template v-else>
            <RouterLink to="/registro" class="btn btn-grande">Crear mi equipo</RouterLink>
            <a href="#como-funciona" class="btn btn-grande btn-cristal">Cómo funciona</a>
          </template>
        </div>
        <ul class="hero-datos entra" style="--d: 0.65s">
          <li><b>10</b><span>equipos</span></li>
          <li><b>50</b><span>jugadores</span></li>
          <li><b>1</b><span>corona</span></li>
        </ul>
      </div>

      <a href="#objetivos" class="hero-bajar" aria-label="Seguir bajando">
        <span>Desliza</span><i aria-hidden="true"></i>
      </a>
    </section>

    <!-- Cinta de frases -->
    <div class="cinta" aria-hidden="true">
      <div class="cinta-pista">
        <template v-for="n in 2" :key="n">
          <span v-for="f in frasesCinta" :key="`${n}-${f}`">{{ f }}<i>◆</i></span>
        </template>
      </div>
    </div>

    <!-- ===================== TÚ ERES EL MANAGER (objetivos) ===================== -->
    <section id="objetivos" class="seccion">
      <div class="contenedor manager">
        <figure class="manager-foto" v-revelar>
          <div class="marco">
            <img :src="fotos.jugador" alt="Un jugador de la LEC concentrado frente a su monitor" loading="lazy" />
          </div>
          <figcaption class="ficha-flotante">
            <span class="micro">Ejemplo de puntuación · ADC</span>
            <span class="cifra">41,5 <small>pts</small></span>
            <span class="tenue">6/2/8 · 300 CS · victoria</span>
          </figcaption>
        </figure>

        <div class="manager-texto">
          <p class="kicker" v-revelar>Nuestros objetivos</p>
          <h2 class="titulo-seccion" v-revelar>Aquí no miras la partida.<br /><span class="texto-acento">La diriges.</span></h2>
          <p class="lead" v-revelar>
            Ellos juegan en el escenario; tú decides desde el banquillo. LEC Fantasy convierte cada partida de la
            competición europea en una en la que tú también juegas.
          </p>
          <ol class="objetivos">
            <li v-for="(o, i) in objetivos" :key="o.t" v-revelar :style="{ '--d': `${i * 0.08}s` }">
              <span class="obj-num">0{{ i + 1 }}</span>
              <div>
                <h3>{{ o.t }}</h3>
                <p class="muted">{{ o.d }}</p>
              </div>
            </li>
          </ol>
        </div>
      </div>
    </section>

    <!-- Frase puente -->
    <section class="puente contenedor" v-revelar>
      <p>Cada decisión es tuya.<br /><span class="texto-acento">Cada punto, también.</span></p>
    </section>

    <!-- ===================== CÓMO FUNCIONA (foto fija + pasos) ===================== -->
    <section id="como-funciona" class="seccion">
      <div class="contenedor">
        <div class="cabeza-seccion" v-revelar>
          <p class="kicker">Cómo funciona</p>
          <h2 class="titulo-seccion">Del sofá al banquillo en tres pasos</h2>
        </div>

        <div class="pasos">
          <div class="pasos-visual" aria-hidden="true">
            <div class="pasos-marco">
              <img
                v-for="(p, i) in pasos"
                :key="p.t"
                :src="p.foto"
                alt=""
                loading="lazy"
                :class="{ activa: pasoActivo === i }"
              />
              <div class="pasos-contador">
                <b>0{{ pasoActivo + 1 }}</b><span>/ 0{{ pasos.length }}</span>
              </div>
              <div class="pasos-barra"><i :style="{ transform: `scaleX(${(pasoActivo + 1) / pasos.length})` }"></i></div>
            </div>
          </div>

          <div class="pasos-lista">
            <article
              v-for="(p, i) in pasos"
              :key="p.t"
              :ref="(el) => (pasosEl[i] = el)"
              :data-indice="i"
              class="paso"
              :class="{ activa: pasoActivo === i }"
            >
              <img :src="p.foto" alt="" loading="lazy" class="paso-foto-movil" />
              <span class="paso-num">Paso 0{{ i + 1 }}</span>
              <h3>{{ p.t }}</h3>
              <p class="muted">{{ p.d }}</p>
              <p class="paso-frase">«{{ p.frase }}»</p>
            </article>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== REGLAS Y PUNTOS ===================== -->
    <section id="reglas" class="seccion reglas">
      <div class="contenedor">
        <div class="cabeza-seccion" v-revelar>
          <p class="kicker">Reglas del juego</p>
          <h2 class="titulo-seccion">Cada kill cuenta</h2>
          <p class="lead muted">Tus titulares puntúan con lo que hacen en la partida real. Así se reparte la gloria:</p>
        </div>

        <div class="puntos">
          <div
            v-for="(r, i) in tablaPuntos"
            :key="r[0]"
            class="punto"
            :class="{ negativo: r[1].startsWith('−') }"
            v-revelar
            :style="{ '--d': `${(i % 4) * 0.06}s` }"
          >
            <span class="punto-valor">{{ r[1] }}</span>
            <span class="punto-nombre">{{ r[0] }}</span>
          </div>
        </div>
        <p class="tenue centrado mt">Ejemplo: un ADC que acaba 6/2/8 con 300 de CS, 30 de visión y gana suma 41,5 puntos.</p>

        <div class="grid grid-2 reglas-tarjetas">
          <div class="tarjeta" v-revelar>
            <h3>Tu plantilla</h3>
            <ul class="lista muted">
              <li>7 jugadores: 5 titulares (uno por rol) y 2 suplentes.</li>
              <li>Solo puntúan los titulares, y cada uno solo en su rol.</li>
              <li>Cada liga tiene su presupuesto. En la Liga Global empiezas con 50 M.</li>
              <li>El mercado se cierra mientras se juega la jornada.</li>
              <li>Los precios suben o bajan hasta un 8 % por jornada según el rendimiento.</li>
            </ul>
          </div>
          <div class="tarjeta tarjeta-acento" v-revelar style="--d: 0.08s">
            <h3>Mejoras</h3>
            <p class="muted">
              Compra una mejora para un jugador antes de la jornada (por ejemplo, «Triple Kill»). Si la consigue y es
              titular, sus puntos se multiplican. Si no, pierdes lo que pagaste.
            </p>
            <p class="frase-corta">Arriesgar también es de manager.</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===================== COMUNIDAD ===================== -->
    <section id="comunidad" class="seccion">
      <div class="contenedor comunidad">
        <div class="comunidad-texto">
          <p class="kicker" v-revelar>Comunidad</p>
          <h2 class="titulo-seccion" v-revelar>Tu liga, tus reglas,<br /><span class="texto-acento">tus amigos.</span></h2>
          <p class="lead" v-revelar>
            Crea una liga privada, comparte el código y que empiece el pique. Al registrarte entras además en la
            Liga Global, contra fans de toda Europa.
          </p>
          <ul class="lista-check" v-revelar>
            <li>Clasificación por jornada con racha de cada rival</li>
            <li>Ligas públicas y privadas con su propio presupuesto</li>
            <li>Noticias de la competición sin salir del juego</li>
          </ul>
        </div>

        <figure class="comunidad-foto" v-revelar>
          <div class="marco marco-alto">
            <img :src="fotos.comunidad" alt="Ambiente en el plató de la LEC" loading="lazy" />
          </div>
          <figcaption class="codigo-flotante">
            <span class="micro">Código de tu liga</span>
            <span class="codigo">LEC·7K2Q</span>
          </figcaption>
        </figure>
      </div>
    </section>

    <!-- ===================== NOTICIAS ===================== -->
    <section v-if="noticias.length" class="seccion noticias">
      <div class="contenedor">
        <div class="fila-entre cabeza-seccion" v-revelar>
          <div>
            <p class="kicker">Actualidad</p>
            <h2 class="titulo-seccion">Últimas noticias</h2>
          </div>
          <RouterLink to="/noticias" class="btn btn-fantasma">Ver todas</RouterLink>
        </div>
        <div class="grid grid-3">
          <RouterLink
            v-for="(n, i) in noticias"
            :key="n.id"
            :to="`/noticias/${n.id}`"
            class="tarjeta noticia"
            v-revelar
            :style="{ '--d': `${i * 0.08}s` }"
          >
            <span class="tenue">{{ fecha(n.fechaPublicacion) }}</span>
            <h3>{{ n.titulo }}</h3>
            <p class="muted">{{ n.resumen || recortar(n.contenido) }}</p>
            <span class="leer">Leer →</span>
          </RouterLink>
        </div>
      </div>
    </section>

    <!-- ===================== LLAMADA FINAL ===================== -->
    <section class="final">
      <div class="contenedor final-in">
        <div class="final-texto" v-revelar>
          <p class="kicker">Tu temporada empieza ahora</p>
          <h2 class="final-titulo">La corona no se sortea.<br /><span class="texto-acento">Se ficha.</span></h2>
          <p class="lead">
            Diez equipos, cincuenta jugadores y un solo trofeo. Elige a tus cinco, ajusta el presupuesto y demuestra
            que sabes de la LEC más que nadie.
          </p>
          <div class="fila">
            <RouterLink v-if="auth.autenticado" :to="rutaInicial(auth)" class="btn btn-grande">Ir a mi panel</RouterLink>
            <template v-else>
              <RouterLink to="/registro" class="btn btn-grande">Empieza gratis</RouterLink>
              <RouterLink to="/login" class="btn btn-grande btn-borde">Ya tengo cuenta</RouterLink>
            </template>
          </div>
        </div>
        <figure class="final-foto" v-revelar style="--d: 0.1s">
          <img :src="fotos.celebracion" alt="Un equipo celebra el título bajo una lluvia de confeti" loading="lazy" />
        </figure>
      </div>
      <p class="contenedor creditos">Fotografías: LEC / Riot Games.</p>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/cliente'
import { fecha } from '../../utils/formato'
import { useAuth } from '../../stores/auth'
import { rutaInicial } from '../../utils/rutas'

import arena from '../../assets/inicio/arena.webp'
import jugador from '../../assets/inicio/jugador.webp'
import trofeo from '../../assets/inicio/trofeo.webp'
import escenario from '../../assets/inicio/escenario.webp'
import grada from '../../assets/inicio/grada.webp'
import comunidad from '../../assets/inicio/comunidad.webp'
import celebracion from '../../assets/inicio/celebracion.webp'

const auth = useAuth()
const fotos = { arena, jugador, comunidad, celebracion }

const frasesCinta = [
  'Tú fichas', 'Tú alineas', 'Tú decides', 'Cada kill cuenta', 'Cinco roles, un presupuesto', 'Una sola corona'
]

const objetivos = [
  { t: 'Acercar la LEC a los fans', d: 'Que seguir la competición europea sea más emocionante y participativo.' },
  { t: 'Premiar la estrategia', d: 'Gana quien mejor entiende el juego: roles, meta, rendimiento y presupuesto.' },
  { t: 'Crear comunidad', d: 'Ligas privadas, clasificaciones y noticias para competir con tus amigos.' }
]

const pasos = [
  {
    t: 'Regístrate',
    d: 'Crea tu cuenta en un minuto. Entrarás automáticamente en la Liga Global con 50 M para gastar.',
    frase: 'El trofeo ya está en el escenario. Solo falta tu nombre.',
    foto: trofeo
  },
  {
    t: 'Forma tu equipo',
    d: 'Ficha a un jugador por rol (Top, Jungla, Mid, ADC y Support) sin pasarte de presupuesto y elige a tus dos suplentes.',
    frase: 'Cinco roles, un presupuesto y ninguna excusa.',
    foto: escenario
  },
  {
    t: 'Compite cada jornada',
    d: 'Tus titulares suman puntos según las partidas reales de la LEC. Ajusta la alineación antes de que se cierre el mercado.',
    frase: 'La grada vibra con cada kill. Tú, además, sumas.',
    foto: grada
  }
]

const tablaPuntos = [
  ['Kill', '+3'], ['Asistencia', '+2'], ['Muerte', '−1'], ['First blood', '+2'],
  ['Cada 50 de CS', '+1'], ['Cada 20 de visión', '+1'], ['Victoria', '+2'], ['MVP de la partida', '+3'],
  ['Doble kill', '+1'], ['Triple kill', '+2'], ['Quadra kill', '+5'], ['Pentakill', '+10']
]

// ---------- Noticias ----------
const noticias = ref([])
const recortar = (t) => (t.length > 140 ? `${t.slice(0, 140)}…` : t)

// ---------- Movimiento ----------
const sinMovimiento = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

// Aparición al entrar en pantalla
const observadorRevelar =
  typeof IntersectionObserver !== 'undefined' && !sinMovimiento
    ? new IntersectionObserver(
        (entradas) => {
          for (const e of entradas) {
            if (e.isIntersecting) {
              e.target.classList.add('visible')
              observadorRevelar.unobserve(e.target)
            }
          }
        },
        { rootMargin: '0px 0px -10% 0px', threshold: 0.12 }
      )
    : null

const vRevelar = {
  mounted(el) {
    el.classList.add('revelar')
    if (observadorRevelar) observadorRevelar.observe(el)
    else el.classList.add('visible')
  },
  unmounted(el) {
    observadorRevelar?.unobserve(el)
  }
}

// Paso activo de "Cómo funciona": el que cruza el centro de la pantalla
const pasoActivo = ref(0)
const pasosEl = []
let observadorPasos = null

// Paralaje de la portada
let paralajes = []
let pendiente = false
function moverParalaje() {
  pendiente = false
  const y = window.scrollY
  for (const { el, v } of paralajes) {
    if (y < window.innerHeight * 1.2) el.style.transform = `translate3d(0, ${y * v}px, 0)`
  }
}
function alHacerScroll() {
  if (!pendiente) {
    pendiente = true
    requestAnimationFrame(moverParalaje)
  }
}

onMounted(async () => {
  await nextTick()
  observadorPasos = new IntersectionObserver(
    (entradas) => {
      for (const e of entradas) if (e.isIntersecting) pasoActivo.value = Number(e.target.dataset.indice)
    },
    { rootMargin: '-45% 0px -45% 0px' }
  )
  pasosEl.forEach((el) => el && observadorPasos.observe(el))

  if (!sinMovimiento) {
    paralajes = [...document.querySelectorAll('[data-paralaje]')].map((el) => ({ el, v: Number(el.dataset.paralaje) }))
    window.addEventListener('scroll', alHacerScroll, { passive: true })
  }

  try {
    noticias.value = (await api.get('/noticias', { tamano: 3 })).contenido
  } catch {
    noticias.value = []
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', alHacerScroll)
  observadorPasos?.disconnect()
  observadorRevelar?.disconnect()
})
</script>

<style scoped>
/* Las secciones sobre foto llevan siempre texto claro, también en modo claro */
.inicio { --claro: #f4f4f5; --claro-2: rgba(244, 244, 245, 0.78); overflow-x: clip; }

/* ---------- Aparición ---------- */
.revelar { opacity: 0; transform: translateY(28px); transition: opacity 0.8s ease, transform 0.8s cubic-bezier(0.2, 0.7, 0.2, 1); transition-delay: var(--d, 0s); }
.revelar.visible { opacity: 1; transform: none; }
.entra { opacity: 0; animation: entrar 0.9s cubic-bezier(0.2, 0.7, 0.2, 1) forwards; animation-delay: var(--d, 0s); }
@keyframes entrar { from { opacity: 0; transform: translateY(24px); } to { opacity: 1; transform: none; } }

/* ---------- Comunes ---------- */
.seccion { padding: clamp(72px, 11vw, 140px) 0; scroll-margin-top: 40px; }
.cabeza-seccion { margin-bottom: clamp(32px, 5vw, 56px); max-width: 760px; }
.cabeza-seccion.fila-entre { max-width: none; align-items: flex-end; }
.titulo-seccion { font-size: clamp(1.9rem, 4.4vw, 3.3rem); letter-spacing: 0.02em; line-height: 1.05; margin-bottom: 0.4em; }
.lead { font-size: clamp(1.02rem, 1.4vw, 1.18rem); color: var(--texto-2); max-width: 620px; }
.marco { position: relative; overflow: hidden; border-radius: 4px 40px 4px 40px; }
.marco img { display: block; width: 100%; height: 100%; object-fit: cover; transition: transform 1.2s cubic-bezier(0.2, 0.7, 0.2, 1); }
.revelar .marco img { transform: scale(1.12); }
.revelar.visible .marco img { transform: scale(1); }

/* ---------- Portada ---------- */
.hero { position: relative; min-height: 100svh; display: flex; align-items: flex-end; color: var(--claro); isolation: isolate; overflow: hidden; }
.hero-fondo { position: absolute; inset: -6% 0 0; z-index: -2; will-change: transform; }
.hero-fondo img { width: 100%; height: 100%; object-fit: cover; object-position: 50% 60%; animation: acercar 14s ease-out forwards; }
@keyframes acercar { from { transform: scale(1.14); } to { transform: scale(1); } }
.hero-velo {
  position: absolute; inset: 0; z-index: -1;
  background:
    linear-gradient(90deg, rgba(5, 5, 7, 0.88) 0%, rgba(5, 5, 7, 0.55) 45%, rgba(5, 5, 7, 0.1) 100%),
    linear-gradient(180deg, rgba(5, 5, 7, 0.55) 0%, transparent 28%, transparent 50%, rgba(5, 5, 7, 0.88) 100%);
}
.hero-contenido { padding-top: 120px; padding-bottom: clamp(96px, 14vh, 150px); }
.hero .kicker { color: var(--acento); }
.hero-titulo { font-size: clamp(2.7rem, 8.2vw, 6.6rem); line-height: 0.96; letter-spacing: 0.01em; margin: 14px 0 22px; text-shadow: 0 4px 40px rgba(0, 0, 0, 0.5); }
.hero-titulo .texto-acento { color: var(--acento); }
.hero-lead { font-size: clamp(1.05rem, 1.6vw, 1.25rem); max-width: 560px; color: var(--claro-2); margin-bottom: 32px; }
.btn-cristal { background: rgba(255, 255, 255, 0.08); color: var(--claro); border-color: rgba(255, 255, 255, 0.28); backdrop-filter: blur(8px); }
.btn-cristal:hover { background: rgba(255, 255, 255, 0.16); filter: none; }
.hero-datos { list-style: none; padding: 0; margin: 44px 0 0; display: flex; gap: clamp(24px, 5vw, 56px); }
.hero-datos li { display: flex; flex-direction: column; border-left: 2px solid var(--acento); padding-left: 14px; }
.hero-datos b { font-family: var(--fuente-titulo); font-size: clamp(1.6rem, 3vw, 2.2rem); line-height: 1; }
.hero-datos span { font-size: 0.7rem; font-weight: 700; letter-spacing: 0.16em; text-transform: uppercase; color: var(--claro-2); margin-top: 6px; }
.hero-bajar { position: absolute; left: 50%; bottom: 26px; transform: translateX(-50%); display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--claro-2); font-size: 0.66rem; font-weight: 700; letter-spacing: 0.2em; text-transform: uppercase; }
.hero-bajar:hover { text-decoration: none; color: var(--claro); }
.hero-bajar i { width: 1px; height: 44px; background: linear-gradient(var(--acento), transparent); animation: bajar 2s ease-in-out infinite; transform-origin: top; }
@keyframes bajar { 0% { transform: scaleY(0); } 50% { transform: scaleY(1); } 100% { transform: scaleY(1); opacity: 0; } }

/* ---------- Cinta ---------- */
.cinta { border-block: 1px solid var(--borde); background: var(--fondo-2); overflow: hidden; padding: 18px 0; }
.cinta-pista { display: flex; width: max-content; animation: deslizar 38s linear infinite; }
.cinta span { font-family: var(--fuente-titulo); font-weight: 800; text-transform: uppercase; letter-spacing: 0.08em; font-size: clamp(1rem, 1.8vw, 1.35rem); color: var(--texto-2); white-space: nowrap; display: inline-flex; align-items: center; }
.cinta i { font-style: normal; color: var(--acento-tinta); font-size: 0.6em; margin: 0 28px; }
@keyframes deslizar { to { transform: translateX(-50%); } }

/* ---------- Manager / objetivos ---------- */
.manager { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr); gap: clamp(40px, 7vw, 110px); align-items: center; }
.manager-foto { position: relative; margin: 0; }
.manager-foto::before { content: ''; position: absolute; inset: 22px -22px -22px 22px; border: 1px solid var(--acento-borde); border-radius: 4px 40px 4px 40px; }
.manager-foto .marco { aspect-ratio: 4 / 5; }
.manager-foto .marco img { object-position: 52% 40%; }
.manager-foto .marco::after { content: ''; position: absolute; inset: 0; background: linear-gradient(200deg, transparent 50%, rgba(5, 5, 7, 0.55)); }
.ficha-flotante {
  position: absolute; right: -28px; bottom: 36px; display: flex; flex-direction: column; gap: 4px;
  background: color-mix(in srgb, var(--tarjeta) 88%, transparent); backdrop-filter: blur(12px);
  border: 1px solid var(--borde-2); border-left: 3px solid var(--acento); border-radius: 10px; padding: 14px 18px; box-shadow: var(--sombra);
}
.ficha-flotante .cifra small { font-size: 0.5em; color: var(--texto-3); }
.objetivos { list-style: none; padding: 0; margin: 36px 0 0; }
.objetivos li { display: flex; gap: 22px; padding: 22px 0; border-top: 1px solid var(--borde); }
.objetivos li:last-child { border-bottom: 1px solid var(--borde); }
.objetivos h3 { font-size: 1.02rem; margin-bottom: 4px; }
.objetivos p { margin: 0; }
.obj-num { font-family: var(--fuente-titulo); font-weight: 800; font-size: 1.1rem; color: var(--acento-tinta); min-width: 32px; }

/* ---------- Frase puente ---------- */
.puente { padding-block: clamp(24px, 6vw, 60px); text-align: center; }
.puente p { font-family: var(--fuente-titulo); font-weight: 800; text-transform: uppercase; font-size: clamp(1.6rem, 4.6vw, 3.6rem); line-height: 1.08; letter-spacing: 0.02em; margin: 0; }

/* ---------- Cómo funciona ---------- */
.pasos { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr); gap: clamp(32px, 6vw, 90px); }
.pasos-visual { position: relative; }
.pasos-marco { position: sticky; top: 14vh; height: 72vh; max-height: 640px; border-radius: 4px 40px 4px 40px; overflow: hidden; background: var(--tarjeta); }
.pasos-marco img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0; transform: scale(1.08); transition: opacity 0.9s ease, transform 1.6s cubic-bezier(0.2, 0.7, 0.2, 1); }
.pasos-marco img.activa { opacity: 1; transform: scale(1); }
.pasos-marco::after { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, transparent 55%, rgba(5, 5, 7, 0.8)); pointer-events: none; }
.pasos-contador { position: absolute; left: 26px; bottom: 30px; z-index: 1; color: var(--claro); display: flex; align-items: baseline; gap: 8px; }
.pasos-contador b { font-family: var(--fuente-titulo); font-size: 2.6rem; line-height: 1; }
.pasos-contador span { color: var(--claro-2); font-weight: 700; letter-spacing: 0.1em; }
.pasos-barra { position: absolute; left: 26px; right: 26px; bottom: 18px; height: 2px; background: rgba(255, 255, 255, 0.2); z-index: 1; }
.pasos-barra i { display: block; height: 100%; background: var(--acento); transform-origin: left; transition: transform 0.6s ease; }
.paso { min-height: 72vh; display: flex; flex-direction: column; justify-content: center; opacity: 0.32; transition: opacity 0.5s ease; }
.paso.activa { opacity: 1; }
.paso:first-child { min-height: 58vh; justify-content: flex-start; padding-top: 8vh; }
.paso:last-child { min-height: 60vh; }
.paso-num { font-size: 0.72rem; font-weight: 800; letter-spacing: 0.18em; text-transform: uppercase; color: var(--acento-tinta); margin-bottom: 12px; }
.paso h3 { font-size: clamp(1.5rem, 3vw, 2.3rem); margin-bottom: 14px; }
.paso p { max-width: 460px; font-size: 1.04rem; }
.paso-frase { font-family: var(--fuente-titulo); font-weight: 700; color: var(--texto); border-left: 2px solid var(--acento); padding-left: 16px; margin-top: 10px; }
.paso-foto-movil { display: none; }

/* ---------- Reglas ---------- */
.reglas { background: var(--fondo-2); border-block: 1px solid var(--borde); }
.puntos { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1px; background: var(--borde); border: 1px solid var(--borde); border-radius: var(--radio); overflow: hidden; }
.punto { background: var(--tarjeta); padding: 26px 22px; display: flex; flex-direction: column; gap: 6px; transition: background 0.25s; }
.punto:hover { background: var(--tarjeta-2); }
.punto-valor { font-family: var(--fuente-titulo); font-weight: 800; font-size: clamp(1.7rem, 3vw, 2.4rem); line-height: 1; color: var(--acento-tinta); }
.punto.negativo .punto-valor { color: var(--rojo); }
.punto-nombre { font-size: 0.72rem; font-weight: 700; letter-spacing: 0.12em; text-transform: uppercase; color: var(--texto-2); }
.reglas-tarjetas { margin-top: 40px; gap: 20px; }
.reglas-tarjetas .tarjeta { padding: 28px; }
.lista { padding-left: 20px; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.frase-corta { font-family: var(--fuente-titulo); font-weight: 800; text-transform: uppercase; letter-spacing: 0.04em; color: var(--acento-tinta); margin: 0; }

/* ---------- Comunidad ---------- */
.comunidad { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: clamp(40px, 7vw, 110px); align-items: center; }
.comunidad-foto { position: relative; margin: 0; }
.comunidad-foto::before { content: ''; position: absolute; inset: -22px 22px 22px -22px; background: var(--acento-suave); border-radius: 40px 4px 40px 4px; }
.comunidad-foto .marco { border-radius: 40px 4px 40px 4px; aspect-ratio: 4 / 3.4; }
.comunidad-foto .marco img { object-position: 60% 40%; }
.codigo-flotante {
  position: absolute; left: -26px; bottom: 30px; display: flex; flex-direction: column; gap: 8px;
  background: color-mix(in srgb, var(--tarjeta) 90%, transparent); backdrop-filter: blur(12px);
  border: 1px solid var(--borde-2); border-radius: 10px; padding: 14px 18px; box-shadow: var(--sombra);
}
.lista-check { list-style: none; padding: 0; margin: 28px 0 0; display: flex; flex-direction: column; gap: 12px; }
.lista-check li { position: relative; padding-left: 30px; color: var(--texto); }
.lista-check li::before { content: ''; position: absolute; left: 0; top: 0.45em; width: 14px; height: 8px; border-left: 2px solid var(--acento); border-bottom: 2px solid var(--acento); transform: rotate(-45deg); }

/* ---------- Noticias ---------- */
.noticias { padding-top: 0; }
.noticia { color: var(--texto); display: flex; flex-direction: column; gap: 8px; padding: 24px; transition: border-color 0.2s, transform 0.25s; }
.noticia:hover { text-decoration: none; border-color: var(--acento-borde); transform: translateY(-4px); }
.noticia h3 { margin: 0; }
.noticia p { margin: 0; flex: 1; }
.leer { font-size: 0.72rem; font-weight: 800; letter-spacing: 0.12em; text-transform: uppercase; color: var(--acento-tinta); }

/* ---------- Final ---------- */
.final { position: relative; padding: clamp(80px, 11vw, 140px) 0 28px; background: radial-gradient(ellipse at 80% 40%, var(--acento-suave), transparent 60%), var(--fondo); border-top: 1px solid var(--borde); }
.final-in { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr); gap: clamp(40px, 6vw, 90px); align-items: center; }
.final-titulo { font-size: clamp(2rem, 4.2vw, 3.6rem); line-height: 1; margin-bottom: 0.45em; }
.final .lead { margin-bottom: 30px; }
.final-foto { margin: 0; position: relative; border-radius: 4px 40px 4px 40px; overflow: hidden; aspect-ratio: 3 / 2; box-shadow: var(--sombra); }
.final-foto img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform 1.4s cubic-bezier(0.2, 0.7, 0.2, 1); }
.final-foto.revelar img { transform: scale(1.1); }
.final-foto.visible img { transform: scale(1); }
.final-foto::after { content: ''; position: absolute; inset: 0; box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08); border-radius: inherit; }
.creditos { margin: clamp(60px, 8vw, 100px) auto 0; font-size: 0.72rem; color: var(--texto-3); }

/* ---------- Responsive ---------- */
@media (max-width: 960px) {
  .manager, .comunidad, .final-in { grid-template-columns: minmax(0, 1fr); }
  .manager-foto { max-width: 520px; width: calc(100% - 22px); }
  .comunidad-foto { order: -1; max-width: 560px; margin-left: 22px; }
  .ficha-flotante { right: -6px; }
  .codigo-flotante { left: -10px; }
  .pasos { grid-template-columns: minmax(0, 1fr); }
  .pasos-visual { display: none; }
  .paso, .paso:first-child, .paso:last-child { min-height: 0; padding: 0 0 56px; opacity: 1; }
  .paso-foto-movil { display: block; width: 100%; aspect-ratio: 16 / 10; object-fit: cover; border-radius: 4px 28px 4px 28px; margin-bottom: 22px; }
  .puntos { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .hero-velo { background: linear-gradient(180deg, rgba(5, 5, 7, 0.6) 0%, rgba(5, 5, 7, 0.45) 40%, rgba(5, 5, 7, 0.85) 75%, rgba(5, 5, 7, 0.94) 100%); }
  .hero-fondo img { object-position: 55% 60%; }
  .hero-contenido { padding-bottom: 110px; }
  .hero .fila .btn { flex: 1; }
  .hero-datos { margin-top: 32px; }
  .manager-foto::before, .comunidad-foto::before { inset: 14px -14px -14px 14px; }
  .comunidad-foto::before { inset: -14px 14px 14px -14px; }
  .punto { padding: 20px 16px; }
  .final .fila .btn { flex: 1; }
}

@media (prefers-reduced-motion: reduce) {
  .entra { animation: none; opacity: 1; }
  .hero-fondo img, .cinta-pista, .hero-bajar i { animation: none; }
  .pasos-marco img, .marco img, .final-foto img { transition: opacity 0.3s; transform: none !important; }
}
</style>
