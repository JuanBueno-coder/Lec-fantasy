// De dónde viene cada noticia: las importadas de los canales oficiales enlazan a la publicación original.
const ORIGENES = {
  X: { nombre: 'X', accion: 'Ver la publicación en X', clase: 'origen-x' },
  YOUTUBE: { nombre: 'YouTube', accion: 'Ver el vídeo en YouTube', clase: 'origen-youtube' },
  MANUAL: { nombre: 'LEC Fantasy', accion: null, clase: 'origen-manual' }
}

export function origenNoticia(n) {
  return ORIGENES[n?.origen] || ORIGENES.MANUAL
}
