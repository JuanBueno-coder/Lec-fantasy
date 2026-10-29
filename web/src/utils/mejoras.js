// Mejoras: textos, etiquetas cortas y la misma explicación de la regla que genera el backend
// (ReglaMejora.java), para la vista previa del formulario de administración.

const num = new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 })

export const CONDICIONES = {
  SIEMPRE: 'Siempre (en todas sus partidas)',
  ESTADISTICA: 'Estadística de la partida…',
  CAMPEON_POCO_JUGADO: 'Juega un campeón poco visto',
  TRIPLE_KILL: 'Triple kill o más',
  QUADRA_KILL: 'Quadra kill o más',
  PENTA_KILL: 'Pentakill',
  FIRST_BLOOD: 'Participa en la first blood',
  MVP: 'MVP de la partida',
  VICTORIA: 'Su equipo gana la partida'
}

/** Estadísticas de una partida. `normal`: lo que vale cada unidad sin mejoras (null = no admite efecto). */
export const ESTADISTICAS = {
  KILLS: { texto: 'kills', normal: 3 },
  MUERTES: { texto: 'muertes', normal: -1 },
  ASISTENCIAS: { texto: 'asistencias', normal: 2 },
  CS: { texto: 'CS', normal: 0.02 },
  VISION: { texto: 'visión', normal: 0.05 },
  DOBLES: { texto: 'double kills', normal: 1 },
  TRIPLES: { texto: 'triple kills', normal: 2 },
  QUADRAS: { texto: 'quadra kills', normal: 5 },
  PENTAS: { texto: 'pentakills', normal: 10 },
  KDA: { texto: 'KDA', normal: null },
  PUNTOS: { texto: 'puntos', normal: null }
}

export const COMPARADORES = { MENOR: '<', MENOR_IGUAL: '≤', IGUAL: '=', MAYOR_IGUAL: '≥', MAYOR: '>' }

export const EFECTOS = {
  MULTIPLICADOR: 'Multiplica los puntos de la partida',
  PUNTOS_POR_ACCION: 'Cambia lo que vale una estadística'
}

export const ESTADO_MEJORA = {
  INVENTARIO: { texto: 'En inventario', clase: 'chip-acento' },
  PENDIENTE: { texto: 'Asignada', clase: 'chip-amarillo' },
  CUMPLIDA: { texto: 'Cumplida', clase: 'chip-verde' },
  FALLIDA: { texto: 'Fallida', clase: 'chip-rojo' }
}

function n(v) {
  return v === null || v === undefined || v === '' ? '?' : num.format(Number(v))
}

function conSigno(v) {
  if (v === null || v === undefined || v === '') return '?'
  const x = Number(v)
  return (x > 0 ? '+' : x < 0 ? '−' : '') + num.format(Math.abs(x))
}

/** Etiqueta corta para chips: "×1,25" o "muertes −0,5". */
export function etiquetaEfecto(m) {
  if (!m) return ''
  if (m.efecto === 'PUNTOS_POR_ACCION') {
    return `${ESTADISTICAS[m.efectoEstadistica]?.texto || '?'} ${conSigno(m.puntosPorUnidad)}`
  }
  return `×${n(m.multiplicador)}`
}

/** "Si muertes < 3 en una partida: puntos de esa partida ×1,5" (igual que ReglaMejora.java). */
export function textoRegla(m) {
  let cond
  switch (m.condicion) {
    case 'SIEMPRE': cond = 'En todas sus partidas de la jornada'; break
    case 'ESTADISTICA':
      cond = m.estadistica && m.comparador
        ? `Si ${ESTADISTICAS[m.estadistica].texto} ${COMPARADORES[m.comparador]} ${n(m.umbral)} en una partida`
        : 'Condición sin configurar'
      break
    case 'CAMPEON_POCO_JUGADO': cond = `Si juega un campeón elegido ${n(m.umbral)} veces o menos en la LEC antes de esa partida`; break
    case 'TRIPLE_KILL': cond = 'Si hace una triple kill (o más) en una partida'; break
    case 'QUADRA_KILL': cond = 'Si hace una quadra kill (o más) en una partida'; break
    case 'PENTA_KILL': cond = 'Si hace una pentakill en una partida'; break
    case 'FIRST_BLOOD': cond = 'Si participa en la primera sangre de una partida'; break
    case 'MVP': cond = 'Si es el MVP de una partida'; break
    case 'VICTORIA': cond = 'Si su equipo gana una partida'; break
    default: cond = '?'
  }
  let efecto
  if (m.efecto === 'PUNTOS_POR_ACCION') {
    const est = ESTADISTICAS[m.efectoEstadistica]
    efecto = est && est.normal !== null
      ? `cada unidad de ${est.texto} vale ${conSigno(m.puntosPorUnidad)} (en lugar de ${conSigno(est.normal)})`
      : 'efecto sin configurar'
  } else {
    efecto = `puntos de esa partida ×${n(m.multiplicador)}`
  }
  return `${cond}: ${efecto}`
}
