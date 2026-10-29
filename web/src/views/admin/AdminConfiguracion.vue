<template>
  <div>
    <h1>Configuración</h1>
    <Alerta :mensaje="mensaje" :tipo="mensajeTipo" class="mb" />

    <div class="fila-entre mt">
      <h2>Mejoras</h2>
      <button class="btn btn-peque" @click="abrirMejora(null)">Nueva mejora</button>
    </div>
    <p class="tenue">Cada mejora tiene una <b>condición</b> (cuándo se cumple en una partida) y un <b>efecto</b> (qué pasa con
      los puntos de esa partida). Se compran al inventario y cada titular puede llevar una por jornada.</p>
    <div class="tarjeta sin-relleno tabla-scroll">
      <table class="tabla">
        <thead><tr><th>Nombre</th><th>Regla</th><th class="num">Precio</th><th>Límite</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          <tr v-for="m in mejoras" :key="m.id">
            <td><b>{{ m.nombre }}</b> <span class="chip chip-acento">{{ etiquetaEfecto(m) }}</span><div class="tenue">{{ m.descripcion }}</div></td>
            <td class="regla">{{ m.regla }}</td>
            <td class="num">{{ m.gratis ? 'Gratis' : millones(m.precio) }}</td>
            <td>{{ m.maxPorEquipo ? `${m.maxPorEquipo} por equipo` : '—' }}</td>
            <td><span class="chip" :class="m.activa ? 'chip-verde' : ''">{{ m.activa ? 'Activa' : 'Inactiva' }}</span></td>
            <td class="derecha"><button class="btn btn-oscuro btn-peque" @click="abrirMejora(m)">Editar</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="fila-entre mt-2">
      <h2>Equipos de la LEC</h2>
      <button class="btn btn-peque" @click="abrirEquipo(null)">Nuevo equipo</button>
    </div>
    <div class="grid grid-3">
      <div v-for="e in equipos" :key="e.id" class="tarjeta fila-entre">
        <div class="fila">
          <img v-if="e.logoUrl" :src="e.logoUrl" alt="" class="logo-eq" />
          <div><b>{{ e.nombre }}</b><div class="tenue">{{ e.siglas || 'sin siglas' }}</div></div>
        </div>
        <button class="btn btn-oscuro btn-peque" @click="abrirEquipo(e)">Editar</button>
      </div>
      <div v-if="!equipos.length" class="tarjeta vacio">Se crean solos al importar la primera jornada.</div>
    </div>

    <Modal :abierto="modalMejora" :titulo="fm.id ? 'Editar mejora' : 'Nueva mejora'" ancho="640px" @cerrar="modalMejora = false">
      <form class="pila" @submit.prevent="guardarMejora">
        <div class="grid grid-2">
          <div class="campo"><label for="mn">Nombre</label><input id="mn" v-model.trim="fm.nombre" class="input" maxlength="60" required /></div>
          <div class="campo"><label for="mp">Precio (M) · 0 = gratis</label><input id="mp" v-model.number="fm.precioM" type="number" step="0.1" min="0" class="input" required /></div>
        </div>
        <div class="campo"><label for="md">Descripción (opcional)</label><input id="md" v-model.trim="fm.descripcion" class="input" maxlength="255" /></div>

        <fieldset class="bloque">
          <legend>Condición · cuándo se cumple en una partida</legend>
          <div class="campo">
            <label for="mc">Tipo</label>
            <select id="mc" v-model="fm.condicion" class="input">
              <option v-for="(t, k) in CONDICIONES" :key="k" :value="k">{{ t }}</option>
            </select>
          </div>
          <div v-if="fm.condicion === 'ESTADISTICA'" class="grid grid-3">
            <div class="campo">
              <label for="me">Estadística</label>
              <select id="me" v-model="fm.estadistica" class="input">
                <option v-for="(e, k) in ESTADISTICAS" :key="k" :value="k">{{ e.texto }}</option>
              </select>
            </div>
            <div class="campo">
              <label for="mco">Comparador</label>
              <select id="mco" v-model="fm.comparador" class="input">
                <option v-for="(t, k) in COMPARADORES" :key="k" :value="k">{{ t }}</option>
              </select>
            </div>
            <div class="campo"><label for="mu">Valor</label><input id="mu" v-model.number="fm.umbral" type="number" step="0.01" class="input" required /></div>
          </div>
          <div v-if="fm.condicion === 'CAMPEON_POCO_JUGADO'" class="campo">
            <label for="mu2">Máximo de veces elegido antes en la LEC</label>
            <input id="mu2" v-model.number="fm.umbral" type="number" step="1" min="0" class="input" required />
            <span class="tenue">Se cuentan las partidas importadas anteriores a cada partida.</span>
          </div>
        </fieldset>

        <fieldset class="bloque">
          <legend>Efecto · qué pasa con los puntos de esa partida</legend>
          <div class="campo">
            <label for="mef">Tipo</label>
            <select id="mef" v-model="fm.efecto" class="input">
              <option v-for="(t, k) in EFECTOS" :key="k" :value="k">{{ t }}</option>
            </select>
          </div>
          <div v-if="fm.efecto === 'MULTIPLICADOR'" class="campo">
            <label for="mm">Multiplicador</label>
            <input id="mm" v-model.number="fm.multiplicador" type="number" step="0.05" min="0.05" max="10" class="input" required />
            <span class="tenue">1,25 = +25 %, 2 = el doble.</span>
          </div>
          <div v-else class="grid grid-2">
            <div class="campo">
              <label for="mee">Estadística</label>
              <select id="mee" v-model="fm.efectoEstadistica" class="input">
                <option v-for="(e, k) in estadisticasConEfecto" :key="k" :value="k">{{ e.texto }} (normal {{ e.normal }})</option>
              </select>
            </div>
            <div class="campo"><label for="mpu">Puntos por unidad</label><input id="mpu" v-model.number="fm.puntosPorUnidad" type="number" step="0.01" class="input" required /></div>
          </div>
        </fieldset>

        <div class="grid grid-2">
          <div class="campo">
            <label for="mmax">Máximo por equipo</label>
            <input id="mmax" v-model.number="fm.maxPorEquipo" type="number" min="1" max="5" class="input" placeholder="Sin límite" />
            <span class="tenue">Cuántas puede tener sin usar a la vez (el Capitán: 1).</span>
          </div>
          <label class="check activa"><input v-model="fm.activa" type="checkbox" /> Activa (se puede comprar)</label>
        </div>

        <div class="vista-previa">
          <span class="micro">Vista previa</span>
          <p><b>{{ fm.nombre || 'Mejora' }}</b> <span class="chip chip-solido">{{ etiquetaEfecto(fm) }}</span>
            <span class="tenue precio-previa">{{ fm.precioM ? millones(fm.precioM * 1e6) : 'Gratis' }}</span></p>
          <p class="sin-margen">{{ textoRegla(fm) }}</p>
        </div>
        <Alerta :mensaje="errorModal" />
        <button class="btn btn-bloque">Guardar</button>
      </form>
    </Modal>

    <Modal :abierto="modalEquipo" :titulo="fe.id ? 'Editar equipo' : 'Nuevo equipo'" @cerrar="modalEquipo = false">
      <form class="pila" @submit.prevent="guardarEquipo">
        <div class="campo"><label for="en">Nombre</label><input id="en" v-model.trim="fe.nombre" class="input" maxlength="100" required /></div>
        <div class="campo"><label for="es">Siglas</label><input id="es" v-model.trim="fe.siglas" class="input" maxlength="10" /></div>
        <div class="campo"><label for="el">URL del logo</label><input id="el" v-model.trim="fe.logoUrl" class="input" maxlength="500" /></div>
        <Alerta :mensaje="errorModal" />
        <button class="btn btn-bloque">Guardar</button>
      </form>
    </Modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import Alerta from '../../components/Alerta.vue'
import Modal from '../../components/Modal.vue'
import { api } from '../../api/cliente'
import { millones } from '../../utils/formato'
import { COMPARADORES, CONDICIONES, EFECTOS, ESTADISTICAS, etiquetaEfecto, textoRegla } from '../../utils/mejoras'

const estadisticasConEfecto = Object.fromEntries(Object.entries(ESTADISTICAS).filter(([, e]) => e.normal !== null))
const NUEVA = { id: null, nombre: '', descripcion: '', precioM: 1, condicion: 'ESTADISTICA', estadistica: 'MUERTES',
  comparador: 'MENOR', umbral: 3, efecto: 'MULTIPLICADOR', multiplicador: 1.5, efectoEstadistica: 'MUERTES',
  puntosPorUnidad: -0.5, maxPorEquipo: null, activa: true }
const mejoras = ref([])
const equipos = ref([])
const mensaje = ref('')
const mensajeTipo = ref('ok')
const errorModal = ref('')
const modalMejora = ref(false)
const modalEquipo = ref(false)
const fm = reactive({ ...NUEVA })
const fe = reactive({ id: null, nombre: '', siglas: '', logoUrl: '' })

async function cargar() {
  ;[mejoras.value, equipos.value] = await Promise.all([api.get('/admin/mejoras'), api.get('/equipos-pro')])
}

function abrirMejora(m) {
  errorModal.value = ''
  Object.assign(fm, { ...NUEVA }, m
    ? { ...m, descripcion: m.descripcion || '', precioM: Number(m.precio) / 1e6,
        umbral: m.umbral === null ? NUEVA.umbral : Number(m.umbral),
        multiplicador: Number(m.multiplicador),
        puntosPorUnidad: m.puntosPorUnidad === null ? NUEVA.puntosPorUnidad : Number(m.puntosPorUnidad),
        estadistica: m.estadistica || NUEVA.estadistica, comparador: m.comparador || NUEVA.comparador,
        efectoEstadistica: m.efectoEstadistica || NUEVA.efectoEstadistica }
    : {})
  modalMejora.value = true
}

async function guardarMejora() {
  const porEstadistica = fm.condicion === 'ESTADISTICA'
  const conUmbral = porEstadistica || fm.condicion === 'CAMPEON_POCO_JUGADO'
  const porAccion = fm.efecto === 'PUNTOS_POR_ACCION'
  const cuerpo = {
    nombre: fm.nombre, descripcion: fm.descripcion, precio: Math.round((fm.precioM || 0) * 1e6),
    condicion: fm.condicion,
    estadistica: porEstadistica ? fm.estadistica : null,
    comparador: porEstadistica ? fm.comparador : null,
    umbral: conUmbral ? fm.umbral : null,
    efecto: fm.efecto,
    multiplicador: porAccion ? null : fm.multiplicador,
    efectoEstadistica: porAccion ? fm.efectoEstadistica : null,
    puntosPorUnidad: porAccion ? fm.puntosPorUnidad : null,
    maxPorEquipo: fm.maxPorEquipo || null,
    activa: fm.activa
  }
  try {
    if (fm.id) await api.put(`/admin/mejoras/${fm.id}`, cuerpo)
    else await api.post('/admin/mejoras', cuerpo)
    modalMejora.value = false
    mensajeTipo.value = 'ok'
    mensaje.value = 'Mejora guardada.'
    cargar()
  } catch (e) {
    errorModal.value = e.message
  }
}

function abrirEquipo(e) {
  errorModal.value = ''
  Object.assign(fe, e ? { id: e.id, nombre: e.nombre, siglas: e.siglas || '', logoUrl: e.logoUrl || '' } : { id: null, nombre: '', siglas: '', logoUrl: '' })
  modalEquipo.value = true
}

async function guardarEquipo() {
  const cuerpo = { nombre: fe.nombre, siglas: fe.siglas, logoUrl: fe.logoUrl }
  try {
    if (fe.id) await api.put(`/admin/equipos-pro/${fe.id}`, cuerpo)
    else await api.post('/admin/equipos-pro', cuerpo)
    modalEquipo.value = false
    mensajeTipo.value = 'ok'
    mensaje.value = 'Equipo guardado.'
    cargar()
  } catch (e) {
    errorModal.value = e.message
  }
}

onMounted(() => cargar().catch((e) => { mensajeTipo.value = 'error'; mensaje.value = e.message }))
</script>

<style scoped>
.sin-relleno { padding: 0; overflow: hidden; }
.logo-eq { width: 36px; height: 36px; object-fit: contain; }
.regla { max-width: 380px; font-size: 0.84rem; }
.bloque { border: 1px solid var(--borde-2); border-radius: 10px; padding: 12px 14px 14px; margin: 0; display: flex; flex-direction: column; gap: 12px; }
.bloque legend { padding: 0 6px; font-size: 0.72rem; font-weight: 800; letter-spacing: 0.04em; color: var(--texto-2); }
.activa { align-self: center; }
.vista-previa { padding: 12px 14px; border-radius: 10px; background: var(--acento-suave); border: 1px solid var(--acento-borde); }
.vista-previa p { margin: 6px 0 0; }
.precio-previa { margin-left: 6px; }
@media (max-width: 620px) { .bloque .grid-3, .grid-2 { grid-template-columns: minmax(0, 1fr); } }
</style>
