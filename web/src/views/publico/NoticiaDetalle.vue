<template>
  <section class="contenedor pagina estrecho">
    <RouterLink to="/noticias">← Noticias</RouterLink>
    <div v-if="cargando" class="cargando">Cargando…</div>
    <Alerta :mensaje="error" class="mt" />
    <article v-if="noticia" class="mt">
      <div class="fila">
        <span class="chip" :class="origen.clase">{{ origen.nombre }}</span>
        <span class="tenue">{{ fechaHora(noticia.fechaPublicacion) }}<template v-if="noticia.autor"> · {{ noticia.autor }}</template></span>
      </div>
      <h1 class="mt">{{ noticia.titulo }}</h1>
      <p v-if="noticia.resumen" class="lead muted">{{ noticia.resumen }}</p>
      <img v-if="noticia.imagenUrl" :src="noticia.imagenUrl" alt="" class="imagen" referrerpolicy="no-referrer" />
      <div v-if="noticia.contenido !== noticia.titulo" class="contenido">{{ noticia.contenido }}</div>
      <a v-if="noticia.urlExterna && origen.accion" :href="noticia.urlExterna" target="_blank" rel="noopener noreferrer"
         class="btn btn-borde mt-2">{{ origen.accion }} ↗</a>
    </article>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Alerta from '../../components/Alerta.vue'
import { api } from '../../api/cliente'
import { fechaHora } from '../../utils/formato'
import { origenNoticia } from '../../utils/noticias'

const route = useRoute()
const noticia = ref(null)
const cargando = ref(true)
const error = ref('')
const origen = computed(() => origenNoticia(noticia.value))

onMounted(async () => {
  try {
    noticia.value = await api.get(`/noticias/${route.params.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    cargando.value = false
  }
})
</script>

<style scoped>
.estrecho { max-width: 780px; }
.lead { font-size: 1.15rem; }
.imagen { width: 100%; border-radius: 12px; margin: 10px 0 20px; }
.origen-youtube { background: #c4161c; color: #fff; }
.origen-x { background: var(--texto); color: var(--fondo); }
.contenido { white-space: pre-line; font-size: 1.05rem; line-height: 1.7; }
</style>
