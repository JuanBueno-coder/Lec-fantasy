<template>
  <div>
    <div class="fila-entre">
      <h1>Jornadas y puntuación</h1>
      <button class="btn" @click="abrirForm(null)">Nueva jornada</button>
    </div>
    <div class="tarjeta pasos tenue">
      <b class="texto-acento">Ciclo de una jornada:</b>
      1. Crear (programada) → 2. Iniciar (cierra el mercado) → 3. Importar partidas → 4. Validar puntos → 5. Finalizar (puntúa equipos, resuelve mejoras, actualiza precios y abre el mercado).
    </div>
    <Alerta :mensaje="mensaje" :tipo="mensajeTipo" class="mt" />

    <div class="pila mt">
      <div v-for="j in jornadas" :key="j.id" class="tarjeta">
        <div class="fila-entre">
          <div>
            <h3 class="sin-margen">{{ j.nombre }}</h3>
            <span class="tenue">{{ fecha(j.fechaInicio) }} – {{ fecha(j.fechaFin) }}<template v-if="j.fuenteOverview"> · {{ j.fuenteOverview }}</template></span>
          </div>
          <span class="chip" :class="claseEstado[j.estado]">{{ ESTADO_JORNADA[j.estado] }}</span>
        </div>
        <div class="fila mt acciones">
          <template v-if="j.estado === 'PROGRAMADA'">
            <button class="btn btn-peque" @click="iniciar(j)">Iniciar</button>
            <button class="btn btn-oscuro btn-peque" @click="abrirForm(j)">Editar</button>
            <button class="btn btn-texto-rojo btn-peque" @click="eliminar(j)">Eliminar</button>
          </template>
          <template v-if="j.estado !== 'FINALIZADA'">
            <button class="btn btn-borde btn-peque" :disabled="ocupado" @click="importarLeaguepedia(j)">Importar de Leaguepedia</button>
            <button class="btn btn-borde btn-peque" :disabled="ocupado" @click="abrirCsv(j)">Subir CSV de Oracle's Elixir</button>
          </template>
          <button class="btn btn-oscuro btn-peque" @click="verPartidas(j)">Partidas</button>
          <button class="btn btn-oscuro btn-peque" @click="previsualizar(j)">Validar puntos</button>
          <button v-if="j.estado === 'EN_CURSO'" class="btn btn-naranja btn-peque" :disabled="ocupado" @click="finalizar(j)">Finalizar jornada</button>
        </div>

        <div v-if="resultadoImport[j.id]" class="alerta alerta-info mt">
          <b>Importación:</b> {{ resultadoImport[j.id].partidasImportadas }} partidas nuevas ·
          {{ resultadoImport[j.id].partidasYaExistentes }} ya estaban · {{ resultadoImport[j.id].partidasFueraDeFecha }} fuera de fechas ·
          {{ resultadoImport[j.id].jugadoresCreados }} jugadores y {{ resultadoImport[j.id].equiposCreados }} equipos nuevos ·
          {{ resultadoImport[j.id].lineasIgnoradas }} líneas ignoradas
          <ul v-if="resultadoImport[j.id].avisos.length" class="avisos">
            <li v-for="a in resultadoImport[j.id].avisos" :key="a">{{ a }}</li>
          </ul>
        </div>

        <div v-if="partidasDe[j.id]" class="mt">
          <div v-if="!partidasDe[j.id].length" class="tenue">Sin partidas.</div>
          <table v-else class="tabla">
            <tbody>
              <tr v-for="p in partidasDe[j.id]" :key="p.id">
                <td class="muted">{{ fechaHora(p.fechaHora) }}</td>
                <td><RouterLink :to="`/partidas/${p.id}`">{{ p.equipoAzul?.nombre }} vs {{ p.equipoRojo?.nombre }}</RouterLink></td>
                <td class="tenue">{{ p.idExterno }}</td>
                <td class="derecha">
                  <button v-if="j.estado !== 'FINALIZADA'" class="btn btn-texto-rojo btn-peque" @click="borrarPartida(j, p)">Borrar</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-if="!jornadas.length" class="tarjeta vacio">Crea la primera jornada para empezar.</div>
    </div>

    <Modal :abierto="modalForm" :titulo="form.id ? 'Editar jornada' : 'Nueva jornada'" @cerrar="modalForm = false">
      <form class="pila" @submit.prevent="guardar">
        <div class="campo"><label for="nom">Nombre</label><input id="nom" v-model.trim="form.nombre" class="input" required placeholder="Semana 1 - Spring Split" /></div>
        <div class="grid grid-2">
          <div class="campo"><label for="fi">Inicio</label><input id="fi" v-model="form.fechaInicio" type="date" class="input" required /></div>
          <div class="campo"><label for="ff">Fin</label><input id="ff" v-model="form.fechaFin" type="date" class="input" required /></div>
        </div>
        <div class="campo">
          <label for="ov">Página de Leaguepedia (opcional)</label>
          <input id="ov" v-model.trim="form.fuenteOverview" class="input" placeholder="LEC/2026 Season/Spring Season" />
          <span class="tenue">Si la dejas vacía se importan todas las partidas de la LEC entre esas fechas.</span>
        </div>
        <Alerta :mensaje="errorModal" />
        <button class="btn btn-bloque">Guardar</button>
      </form>
    </Modal>

    <Modal :abierto="!!csvJornada" :titulo="`Subir CSV · ${csvJornada?.nombre}`" @cerrar="csvJornada = null">
      <form class="pila" @submit.prevent="subirCsv">
        <p class="muted">Descárgalo en oracleselixir.com → Tools → Downloads (archivo del año). Solo se importan las partidas de la liga indicada entre las fechas de la jornada.</p>
        <input type="file" accept=".csv,text/csv" class="input" required @change="archivo = $event.target.files[0]" />
        <div class="campo"><label for="liga">Liga</label><input id="liga" v-model.trim="liga" class="input" /></div>
        <Alerta :mensaje="errorModal" />
        <button class="btn btn-bloque" :disabled="!archivo || ocupado">{{ ocupado ? 'Importando…' : 'Importar' }}</button>
      </form>
    </Modal>

    <Modal :abierto="!!previa" :titulo="`Puntos · ${previa?.jornada.nombre}`" ancho="760px" @cerrar="previa = null">
      <p class="muted">{{ previa?.partidas }} partidas importadas. Revisa los puntos antes de finalizar.</p>
      <div class="tabla-scroll">
        <table class="tabla">
          <thead><tr><th>Rol</th><th>Jugador</th><th>Equipo</th><th class="num">Partidas</th><th class="num">Puntos</th></tr></thead>
          <tbody>
            <tr v-for="f in previa?.jugadores || []" :key="f.jugadorId">
              <td><Posicion :posicion="f.posicion" /></td><td class="negrita">{{ f.nickname }}</td>
              <td class="muted">{{ f.equipo }}</td><td class="num">{{ f.partidas }}</td><td class="num negrita">{{ puntos(f.puntos) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </Modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import Alerta from '../../components/Alerta.vue'
import Modal from '../../components/Modal.vue'
import Posicion from '../../components/Posicion.vue'
import { api } from '../../api/cliente'
import { ESTADO_JORNADA, fecha, fechaHora, puntos } from '../../utils/formato'

const claseEstado = { PROGRAMADA: 'chip-acento', EN_CURSO: 'chip-naranja', FINALIZADA: 'chip-verde' }
const jornadas = ref([])
const mensaje = ref('')
const mensajeTipo = ref('ok')
const ocupado = ref(false)
const modalForm = ref(false)
const errorModal = ref('')
const form = reactive({ id: null, nombre: '', fechaInicio: '', fechaFin: '', fuenteOverview: '' })
const csvJornada = ref(null)
const archivo = ref(null)
const liga = ref('LEC')
const previa = ref(null)
const resultadoImport = reactive({})
const partidasDe = reactive({})

function avisar(texto, tipo = 'ok') {
  mensaje.value = texto
  mensajeTipo.value = tipo
}

async function cargar() {
  jornadas.value = (await api.get('/jornadas')).slice().reverse()
}

async function accion(fn, exito) {
  ocupado.value = true
  mensaje.value = ''
  try {
    const r = await fn()
    if (exito) avisar(typeof exito === 'function' ? exito(r) : exito)
    await cargar()
    return r
  } catch (e) {
    avisar(e.message, 'error')
  } finally {
    ocupado.value = false
  }
}

function abrirForm(j) {
  errorModal.value = ''
  Object.assign(form, j ? { ...j, fuenteOverview: j.fuenteOverview || '' } : { id: null, nombre: '', fechaInicio: '', fechaFin: '', fuenteOverview: '' })
  modalForm.value = true
}

async function guardar() {
  errorModal.value = ''
  const cuerpo = { nombre: form.nombre, fechaInicio: form.fechaInicio, fechaFin: form.fechaFin, fuenteOverview: form.fuenteOverview }
  try {
    if (form.id) await api.put(`/admin/jornadas/${form.id}`, cuerpo)
    else await api.post('/admin/jornadas', cuerpo)
    modalForm.value = false
    avisar('Jornada guardada.')
    cargar()
  } catch (e) {
    errorModal.value = e.message
  }
}

const iniciar = (j) => confirm(`¿Iniciar ${j.nombre}? El mercado se cerrará para todos.`) &&
  accion(() => api.post(`/admin/jornadas/${j.id}/iniciar`), `${j.nombre} en curso. Mercado cerrado.`)

const eliminar = (j) => confirm(`¿Eliminar ${j.nombre}? Se devolverá el dinero de las mejoras compradas para ella.`) &&
  accion(() => api.del(`/admin/jornadas/${j.id}`), 'Jornada eliminada.')

async function importarLeaguepedia(j) {
  avisar('Consultando Leaguepedia… puede tardar un poco.', 'info')
  const r = await accion(() => api.post(`/admin/jornadas/${j.id}/importar/leaguepedia`))
  if (r) {
    resultadoImport[j.id] = r
    mensaje.value = ''
    delete partidasDe[j.id]
  }
}

function abrirCsv(j) {
  errorModal.value = ''
  archivo.value = null
  csvJornada.value = j
}

async function subirCsv() {
  const j = csvJornada.value
  const datos = new FormData()
  datos.append('archivo', archivo.value)
  datos.append('liga', liga.value || 'LEC')
  errorModal.value = ''
  ocupado.value = true
  try {
    resultadoImport[j.id] = await api.post(`/admin/jornadas/${j.id}/importar/csv`, datos)
    delete partidasDe[j.id]
    csvJornada.value = null
  } catch (e) {
    errorModal.value = e.message
  } finally {
    ocupado.value = false
  }
}

async function verPartidas(j) {
  if (partidasDe[j.id]) {
    delete partidasDe[j.id]
    return
  }
  try {
    partidasDe[j.id] = await api.get(`/jornadas/${j.id}/partidas`)
  } catch (e) {
    avisar(e.message, 'error')
  }
}

async function borrarPartida(j, p) {
  if (!confirm(`¿Borrar la partida ${p.equipoAzul?.nombre} vs ${p.equipoRojo?.nombre}?`)) return
  try {
    await api.del(`/admin/partidas/${p.id}`)
    partidasDe[j.id] = await api.get(`/jornadas/${j.id}/partidas`)
  } catch (e) {
    avisar(e.message, 'error')
  }
}

async function previsualizar(j) {
  try {
    previa.value = await api.get(`/admin/jornadas/${j.id}/previsualizacion`)
  } catch (e) {
    avisar(e.message, 'error')
  }
}

const finalizar = (j) => confirm(`¿Finalizar ${j.nombre}? Se puntuará a todos los equipos y no se podrá deshacer.`) &&
  accion(() => api.post(`/admin/jornadas/${j.id}/finalizar`),
    (r) => `${j.nombre} finalizada: ${r.equiposPuntuados} equipos puntuados, ${r.mejorasCumplidas} mejoras cumplidas, ${r.mejorasFallidas} fallidas, ${r.preciosActualizados} precios actualizados. Mercado abierto.`)

onMounted(() => cargar().catch((e) => avisar(e.message, 'error')))
</script>

<style scoped>
.pasos { margin-top: 8px; }
.sin-margen { margin: 0; }
.acciones { gap: 8px; }
.avisos { margin: 6px 0 0; padding-left: 18px; }
</style>
