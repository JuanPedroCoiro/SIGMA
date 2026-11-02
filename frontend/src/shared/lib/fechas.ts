// Las fechas llegan del backend en UTC; se muestran siempre en hora de Montevideo.
const ZONA = 'America/Montevideo'

const formatoHora = new Intl.DateTimeFormat('es-UY', {
  timeZone: ZONA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const formatoDia = new Intl.DateTimeFormat('es-UY', {
  timeZone: ZONA,
  day: '2-digit',
  month: '2-digit',
})

const formatoFechaCompleta = new Intl.DateTimeFormat('es-UY', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/**
 * Hora en Montevideo («15:30»). Si no es el mismo día que `ahora`, agrega la fecha
 * («15:30 del 28/10»).
 */
export function formatearHoraMontevideo(instante: string | Date, ahora: Date = new Date()) {
  const fecha = new Date(instante)
  if (formatoFechaCompleta.format(fecha) === formatoFechaCompleta.format(ahora)) {
    return formatoHora.format(fecha)
  }
  return formatearFechaHoraMontevideo(fecha)
}

/** Hora y fecha en Montevideo, siempre con la fecha («15:30 del 28/10»), aunque sea hoy. */
export function formatearFechaHoraMontevideo(instante: string | Date) {
  const fecha = new Date(instante)
  return `${formatoHora.format(fecha)} del ${formatoDia.format(fecha)}`
}
