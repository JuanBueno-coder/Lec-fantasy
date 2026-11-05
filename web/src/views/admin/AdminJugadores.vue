<template>
  <div>
    <div class="fila-entre">
      <h1>Jugadores y precios</h1>
      <div class="fila">
        <button class="btn btn-borde" :disabled="trayendo" @click="traerFotos">
          {{ trayendo ? 'Trayendo fotos…' : 'Traer fotos de Leaguepedia' }}
        </button>
        <button class="btn" @click="abrir(null)">Nuevo jugador</button>
      </div>
    </div>
    <p class="muted">Los jugadores y equipos se crean solos al importar partidas. Aquí puedes corregir datos y ajustar precios.
      Las fotos salen de la ficha de cada jugador en Leaguepedia; la automatización semanal trae las de los jugadores nuevos.</p>
    <label class="check mb"><input v-model="todasLasFotos" type="checkbox" />
      Comprobar también las que ya tienen foto (p. ej. al empezar un split; sustituye las URL puestas a mano)</label>
    <Alerta :mensaje="mensaje" :tipo="mensajeTipo" class="mb" />
    <div class="fila mb">
      <input v-model.trim="texto" class="input buscar" placeholder="Buscar…" />
      <select v-model="posicion" class="input selector">
        <option value="">Todos los roles</option>
        <option v-for="p in POSICIONES" :key="p" :value="p">{{ p }}</option>
      </select>
    </div>
    <div class="tarjeta sin-relleno tabla-scroll">
      <table class="tabla">
        <thead><tr><th>Rol</th><th>Jugador</th><th>Equipo</th><th class="num">Partidas</th><th class="num">Puntos</th><th class="num">Valor</th><th></th></tr></thead>
        <tbody>
          <tr v-for="j in filtrados" :key="j.id">
            <td><Posicion :posicion="j.posicion" /></td>
            <td class="negrita">
              <span class="jugador"><AvatarJugador :jugador="j" class="anillo" />{{ j.nickname }}</span>
              <span v-if="j.bloqueado" class="chip chip-rojo">bloqueado</span>
            </td>
            <td class="muted">{{ j.equipo?.nombre || '—' }}</td>
            <td class="num">{{ j.partidasJugadas }}</td>
            <td class="num">{{ puntos(j.puntosTotales) }}</td>
            <td class="num negrita">{{ millones(j.valorMercado) }}</td>
            <td class="derecha"><button class="btn btn-oscuro btn-peque" @click="abrir(j)">Editar</button></td>
          </tr>
          <tr v-if="!filtrados.length"><td colspan="7" class="vacio">No hay jugadores.</td></tr>
        </tbody>
      </table>
    </div>

    <Modal :abierto="modal" :titulo="form.id ? `Editar ${form.nickname}` : 'Nuevo jugador'" @cerrar="modal = false">
      <form class="pila" @submit.prevent="guardar">
        <div class="grid grid-2">
          <div class="campo"><label for="nick">Nickname</label><input id="nick" v-model.trim="form.nickname" class="input" required maxlength="40" /></div>
          <div class="campo"><label for="nr">Nombre real</label><input id="nr" v-model.trim="form.nombreReal" class="input" maxlength="100" /></div>
          <div class="campo">
            <label for="pos">Rol</label>
            <select id="pos" v-model="form.posicion" class="input" required>
              <option v-for="p in POSICIONES" :key="p" :value="p">{{ NOMBRE_POSICION[p] }}</option>
            </select>
          </div>
          <div class="campo">
            <label for="eq">Equipo</label>
            <select id="eq" v-model="form.equipoId" class="input">
              <option :value="null">Sin equipo</option>
              <option v-for="e in equipos" :key="e.id" :value="e.id">{{ e.nombre }}</option>
            </select>
          </div>
          <div class="campo">
            <label for="val">Valor de mercado (M)</label>
            <input id="val" v-model.number="form.valorM" type="number" step="0.01" min="0.1" class="input" required />
          </div>
          <div class="campo">
            <label for="foto">URL de la foto</label>
            <input id="foto" v-model.trim="form.fotoUrl" class="input" maxlength="500" />
            <span class="tenue">Si la cambias o la borras, se quita la traída de Leaguepedia.</span>
          </div>
        </div>
        <Alerta :mensaje="errorModal" />
        <button class="btn btn-bloque">Guardar</button>
      </form>
    </Modal>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import Alerta from '../../components/Alerta.vue'
import AvatarJugador from '../../components/AvatarJugador.vue'
import Modal from '../../components/Modal.vue'
import Posicion from '../../components/Posicion.vue'
import { api } from '../../api/cliente'
import { millones, puntos, POSICIONES, NOMBRE_POSICION } from '../../utils/formato'

const jugadores = ref([])
const equipos = ref([])
const texto = ref('')
const posicion = ref('')
const modal = ref(false)
const errorModal = ref('')
const mensaje = ref('')
const mensajeTipo = ref('ok')
const trayendo = ref(false)
const todasLasFotos = ref(false)
const form = reactive({ id: null, nickname: '', nombreReal: '', posicion: 'TOP', equipoId: null, valorM: 8, fotoUrl: '' })

const filtrados = computed(() => jugadores.value.filter((j) =>
  (!posicion.value || j.posicion === posicion.value) &&
  (!texto.value || j.nickname.toLowerCase().includes(texto.value.toLowerCase()))))

async function cargar() {
  ;[jugadores.value, equipos.value] = await Promise.all([api.get('/admin/jugadores'), api.get('/equipos-pro')])
}

function abrir(j) {
  errorModal.value = ''
  Object.assign(form, j
    ? { id: j.id, nickname: j.nickname, nombreReal: j.nombreReal || '', posicion: j.posicion, equipoId: j.equipo?.id ?? null,
        valorM: Number(j.valorMercado) / 1e6, fotoUrl: j.fotoUrl || '' }
    : { id: null, nickname: '', nombreReal: '', posicion: 'TOP', equipoId: null, valorM: 8, fotoUrl: '' })
  modal.value = true
}

async function guardar() {
  errorModal.value = ''
  const cuerpo = { nickname: form.nickname, nombreReal: form.nombreReal, posicion: form.posicion, equipoId: form.equipoId,
    valorMercado: Math.round(form.valorM * 1e6), fotoUrl: form.fotoUrl }
  try {
    if (form.id) await api.put(`/admin/jugadores/${form.id}`, cuerpo)
    else await api.post('/admin/jugadores', cuerpo)
    modal.value = false
    mensajeTipo.value = 'ok'
    mensaje.value = `${form.nickname} guardado.`
    cargar()
  } catch (e) {
    errorModal.value = e.message
  }
}

async function traerFotos() {
  mensaje.value = ''
  try {
    const estado = await api.post(`/admin/jugadores/fotos/leaguepedia?todas=${todasLasFotos.value}`)
    seguirFotos(estado)
  } catch (e) {
    mensajeTipo.value = 'error'
    mensaje.value = e.message
  }
}

// Leaguepedia limita las consultas, así que las fotos se traen en segundo plano y aquí se va mirando el estado
let temporizador = null
function seguirFotos(estado) {
  clearTimeout(temporizador)
  trayendo.value = !!estado?.enCurso
  if (estado?.enCurso) {
    mensajeTipo.value = 'ok'
    mensaje.value = 'Trayendo fotos de Leaguepedia… Sin cuenta de bot solo deja una consulta por minuto, así que puede tardar unos minutos. Puedes seguir usando el panel.'
    temporizador = setTimeout(async () => {
      try {
        seguirFotos(await api.get('/admin/jugadores/fotos/leaguepedia'))
      } catch {
        seguirFotos(estado)
      }
    }, 5000)
    return
  }
  if (!estado?.fin) return
  if (estado.error) {
    mensajeTipo.value = 'error'
    mensaje.value = estado.error
    return
  }
  const r = estado.resultado || {}
  const sinFoto = r.sinFoto || []
  const errores = r.errores || []
  const partes = [`${r.actualizadas} foto(s) nuevas o actualizadas`]
  if (r.sinCambios) partes.push(`${r.sinCambios} sin cambios`)
  if (sinFoto.length) partes.push(`sin foto en Leaguepedia: ${sinFoto.join(', ')}`)
  if (errores.length) partes.push(`errores: ${errores.join('; ')}`)
  mensajeTipo.value = errores.length ? 'error' : 'ok'
  mensaje.value = r.revisados ? partes.join(' · ') + '.' : 'Todos los jugadores tienen ya foto.'
  cargar()
}

onUnmounted(() => clearTimeout(temporizador))

onMounted(() => {
  cargar().catch((e) => { mensajeTipo.value = 'error'; mensaje.value = e.message })
  // Si ya se estaban trayendo (p. ej. al volver a esta pantalla), se sigue mostrando el progreso
  api.get('/admin/jugadores/fotos/leaguepedia').then((e) => { if (e?.enCurso) seguirFotos(e) }).catch(() => {})
})
</script>

<style scoped>
.sin-relleno { padding: 0; overflow: hidden; }
.buscar { max-width: 280px; }
.selector { width: auto; }
.jugador { display: inline-flex; align-items: center; gap: 10px; }
.jugador .avatar { width: 30px; height: 30px; font-size: 0.68rem; }
</style>
