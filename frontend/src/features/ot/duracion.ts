/** Minutos como «2 h 05 min», «45 min» o «3 d 4 h». */
export function formatearDuracion(minutos: number): string {
  const total = Math.max(0, Math.round(minutos))
  const dias = Math.floor(total / 1440)
  const horas = Math.floor((total % 1440) / 60)
  const resto = total % 60
  if (dias > 0) return horas > 0 ? `${dias} d ${horas} h` : `${dias} d`
  if (horas > 0) return `${horas} h ${String(resto).padStart(2, '0')} min`
  return `${resto} min`
}
