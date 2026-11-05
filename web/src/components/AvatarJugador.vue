<template>
  <!-- Foto del jugador (Leaguepedia o la URL del administrador); si no hay o falla, sus iniciales -->
  <span class="avatar" :class="{ 'con-foto': src }">
    <img v-if="src" :src="src" alt="" loading="lazy" referrerpolicy="no-referrer" @error="fallo = true" />
    <template v-else>{{ iniciales(jugador?.nickname) }}</template>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { urlRecurso } from '../api/cliente'
import { iniciales } from '../utils/formato'

const props = defineProps({ jugador: { type: Object, default: null } })
const fallo = ref(false)
watch(() => props.jugador?.fotoUrl, () => { fallo.value = false })
const src = computed(() => (fallo.value ? null : urlRecurso(props.jugador?.fotoUrl)))
</script>

<style scoped>
.avatar.con-foto { overflow: hidden; padding: 0; }
.avatar img { width: 100%; height: 100%; object-fit: cover; object-position: top; border-radius: inherit; display: block; }
</style>
