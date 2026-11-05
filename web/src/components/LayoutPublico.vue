<template>
  <div class="layout">
    <!-- Sin barra de navegación: la portada es una sola página de scroll.
         En inicio la cabecera flota transparente sobre la foto y se vuelve sólida al bajar. -->
    <header class="cabecera" :class="{ flotante: enInicio, transparente: enInicio && !bajado }">
      <div class="contenedor cabecera-in">
        <Logo />
        <div class="acciones">
          <template v-if="auth.autenticado">
            <RouterLink :to="rutaInicial(auth)" class="btn">Ir a mi panel</RouterLink>
          </template>
          <template v-else>
            <RouterLink to="/login" class="btn btn-borde">Iniciar sesión</RouterLink>
            <RouterLink to="/registro" class="btn ocultar-movil">Registrarse</RouterLink>
          </template>
        </div>
      </div>
      <div v-if="enInicio" class="progreso" aria-hidden="true">
        <i :style="{ transform: `scaleX(${progreso})` }"></i>
      </div>
    </header>
    <main><RouterView /></main>
    <Pie />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Logo from './Logo.vue'
import Pie from './Pie.vue'
import { useAuth } from '../stores/auth'
import { rutaInicial } from '../utils/rutas'

const auth = useAuth()
const route = useRoute()
const enInicio = computed(() => route.name === 'inicio')

const bajado = ref(false)
const progreso = ref(0)
let pendiente = false

function medir() {
  pendiente = false
  const y = window.scrollY
  const total = document.documentElement.scrollHeight - window.innerHeight
  bajado.value = y > 40
  progreso.value = total > 0 ? Math.min(1, y / total) : 0
}
function alHacerScroll() {
  if (!pendiente) {
    pendiente = true
    requestAnimationFrame(medir)
  }
}

onMounted(() => {
  medir()
  window.addEventListener('scroll', alHacerScroll, { passive: true })
  window.addEventListener('resize', alHacerScroll)
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', alHacerScroll)
  window.removeEventListener('resize', alHacerScroll)
})
</script>

<style scoped>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
main { flex: 1; }
.cabecera {
  background: var(--fondo-2); border-bottom: 1px solid var(--borde); position: sticky; top: 0; z-index: 20;
  transition: background-color 0.35s, border-color 0.35s, backdrop-filter 0.35s;
}
.cabecera-in { display: flex; align-items: center; gap: 24px; padding-top: 14px; padding-bottom: 14px; }
.acciones { margin-left: auto; display: flex; gap: 12px; align-items: center; }

/* Portada: cabecera encima de la foto */
.cabecera.flotante {
  position: fixed; left: 0; right: 0;
  background: color-mix(in srgb, var(--fondo-2) 82%, transparent);
  backdrop-filter: blur(14px);
}
.cabecera.transparente { background: transparent; border-bottom-color: transparent; backdrop-filter: none; }
.cabecera.transparente :deep(.logo) { color: #f4f4f5; }
.cabecera.transparente .btn-borde { color: #f4f4f5; border-color: rgba(255, 255, 255, 0.35); }
.cabecera.transparente .btn-borde:hover { background: rgba(255, 255, 255, 0.1); }

.progreso { position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; pointer-events: none; }
.progreso i { display: block; height: 100%; background: var(--acento); transform-origin: left; }
</style>
