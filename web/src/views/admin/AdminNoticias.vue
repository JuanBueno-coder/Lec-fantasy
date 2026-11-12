<template>
  <div>
    <div class="fila-entre">
      <h1>Publicar noticias</h1>
      <button class="btn" @click="abrir(null)">Nueva noticia</button>
    </div>
    <Alerta :mensaje="mensaje" :tipo="mensajeTipo" class="mb" />

    <!-- Canales oficiales: X (Twitter) y YouTube de la LEC -->
    <section v-if="fuentes" class="tarjeta mt">
      <div class="tarjeta-cabeza">
        <div>
          <h2>Canales oficiales</h2>
          <span class="tenue">
            Se importan solas cada 30 minutos{{ fuentes.automaticas ? '' : ' (desactivado: NOTICIAS_AUTOMATICAS=false)' }}
            y se publican directamente. Puedes ocultarlas o borrarlas como cualquier otra.
          </span>
        </div>
        <button class="btn btn-peque" :disabled="importando" @click="importar">
          {{ importando ? 'Importando…' : 'Importar ahora' }}
        </button>
      </div>
      <div class="canales">
        <div class="canal">
          <span class="chip" :class="fuentes.x ? 'chip-verde' : 'chip-amarillo'">X · {{ fuentes.x ? 'activo' : 'sin token' }}</span>
          <span class="tenue">
            <template v-if="fuentes.x">{{ fuentes.cuentasX.map((c) => '@' + c).join(', ') }}</template>
            <template v-else>Añade X_BEARER_TOKEN en las variables del backend (API de pago por uso)</template>
          </span>
        </div>
        <div class="canal">
          <span class="chip" :class="fuentes.canalesYoutube.length ? 'chip-verde' : 'chip-amarillo'">YouTube · {{ fuentes.canalesYoutube.length ? 'activo' : 'sin canales' }}</span>
          <span class="tenue">{{ fuentes.canalesYoutube.join(', ') || 'YOUTUBE_CANALES vacío' }}</span>
        </div>
      </div>
      <div v-if="fuentes.ultima" class="ultima">
        <span class="micro">Última importación · {{ fechaHora(fuentes.ultima.fecha) }}</span>
        <div v-for="c in fuentes.ultima.canales" :key="c.origen + c.cuenta" class="fila">
          <b>{{ c.origen === 'X' ? 'X' : 'YouTube' }} {{ c.cuenta }}</b>
          <span v-if="c.error" class="texto-rojo">{{ c.error }}</span>
          <span v-else class="tenue">{{ c.nuevas }} {{ c.nuevas === 1 ? 'nueva' : 'nuevas' }}</span>
        </div>
      </div>
    </section>

    <div class="pila mt">
      <div v-for="n in noticias" :key="n.id" class="tarjeta fila-entre">
        <div>
          <span class="chip">{{ origenNoticia(n).nombre }}</span>
          <b>{{ n.titulo }}</b>
          <span v-if="!n.publicada" class="chip chip-amarillo">{{ n.origen === 'MANUAL' ? 'borrador' : 'oculta' }}</span>
          <div class="tenue">{{ fechaHora(n.fechaPublicacion) }}<template v-if="n.autor"> · {{ n.autor }}</template></div>
        </div>
        <div class="fila">
          <RouterLink v-if="n.publicada" :to="`/noticias/${n.id}`" class="btn btn-oscuro btn-peque">Ver</RouterLink>
          <a v-if="n.urlExterna" :href="n.urlExterna" target="_blank" rel="noopener noreferrer" class="btn btn-oscuro btn-peque">Original ↗</a>
          <button class="btn btn-oscuro btn-peque" @click="abrir(n)">Editar</button>
          <button class="btn btn-texto-rojo btn-peque" @click="eliminar(n)">Eliminar</button>
        </div>
      </div>
      <div v-if="!noticias.length" class="tarjeta vacio">Aún no hay noticias.</div>
    </div>

    <Modal :abierto="modal" :titulo="form.id ? 'Editar noticia' : 'Nueva noticia'" ancho="720px" @cerrar="modal = false">
      <form class="pila" @submit.prevent="guardar">
        <div class="campo"><label for="t">Título</label><input id="t" v-model.trim="form.titulo" class="input" maxlength="200" required /></div>
        <div class="campo"><label for="r">Resumen</label><input id="r" v-model.trim="form.resumen" class="input" maxlength="500" /></div>
        <div class="campo"><label for="c">Contenido</label><textarea id="c" v-model="form.contenido" class="input contenido" maxlength="10000" required></textarea></div>
        <div class="campo"><label for="i">URL de imagen</label><input id="i" v-model.trim="form.imagenUrl" class="input" maxlength="500" /></div>
        <label class="check"><input v-model="form.publicada" type="checkbox" /> Publicada (visible para todos)</label>
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
import { fechaHora } from '../../utils/formato'
import { origenNoticia } from '../../utils/noticias'

const noticias = ref([])
const modal = ref(false)
const errorModal = ref('')
const mensaje = ref('')
const mensajeTipo = ref('ok')
const fuentes = ref(null)
const importando = ref(false)
const form = reactive({ id: null, titulo: '', resumen: '', contenido: '', imagenUrl: '', publicada: true })

async function cargar() {
  noticias.value = (await api.get('/admin/noticias', { tamano: 50 })).contenido
}

async function cargarFuentes() {
  fuentes.value = await api.get('/admin/noticias/fuentes')
}

async function importar() {
  importando.value = true
  try {
    const r = await api.post('/admin/noticias/importar')
    const fallos = r.canales.filter((c) => c.error).length
    mensajeTipo.value = fallos ? 'error' : 'ok'
    mensaje.value = `${r.nuevas} ${r.nuevas === 1 ? 'noticia nueva' : 'noticias nuevas'}` +
      (fallos ? ` · ${fallos} ${fallos === 1 ? 'canal ha fallado' : 'canales han fallado'}` : '')
    await Promise.all([cargar(), cargarFuentes()])
  } catch (e) {
    mensajeTipo.value = 'error'
    mensaje.value = e.message
  } finally {
    importando.value = false
  }
}

function abrir(n) {
  errorModal.value = ''
  Object.assign(form, n
    ? { id: n.id, titulo: n.titulo, resumen: n.resumen || '', contenido: n.contenido, imagenUrl: n.imagenUrl || '', publicada: n.publicada }
    : { id: null, titulo: '', resumen: '', contenido: '', imagenUrl: '', publicada: true })
  modal.value = true
}

async function guardar() {
  const cuerpo = { titulo: form.titulo, resumen: form.resumen, contenido: form.contenido, imagenUrl: form.imagenUrl, publicada: form.publicada }
  try {
    if (form.id) await api.put(`/admin/noticias/${form.id}`, cuerpo)
    else await api.post('/admin/noticias', cuerpo)
    modal.value = false
    mensajeTipo.value = 'ok'
    mensaje.value = 'Noticia guardada.'
    cargar()
  } catch (e) {
    errorModal.value = e.message
  }
}

async function eliminar(n) {
  if (!confirm(`¿Eliminar «${n.titulo}»?`)) return
  try {
    await api.del(`/admin/noticias/${n.id}`)
    cargar()
  } catch (e) {
    mensajeTipo.value = 'error'
    mensaje.value = e.message
  }
}

onMounted(() => Promise.all([cargar(), cargarFuentes()]).catch((e) => { mensajeTipo.value = 'error'; mensaje.value = e.message }))
</script>

<style scoped>
.contenido { min-height: 220px; }
.canales { display: flex; flex-direction: column; gap: 8px; }
.canal { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ultima { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--borde); display: flex; flex-direction: column; gap: 6px; }
</style>
