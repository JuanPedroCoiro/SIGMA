const formatoCoordenada = new Intl.NumberFormat('es-UY', {
  minimumFractionDigits: 6,
  maximumFractionDigits: 6,
})

/** Latitud o longitud con 6 decimales y coma decimal («-34,712345»). */
export function formatearCoordenada(valor: number) {
  return formatoCoordenada.format(valor)
}
