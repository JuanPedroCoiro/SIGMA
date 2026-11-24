/**
 * Enlace «Cómo llegar» a la instalación (HU-07): la URL universal de Google Maps, que en el
 * celular abre la app de mapas con el recorrido. Solo lleva las coordenadas del destino. Sin
 * coordenadas, null.
 */
export function enlaceComoLlegar(latitud: number | null, longitud: number | null) {
  if (latitud === null || longitud === null) return null
  return `https://www.google.com/maps/dir/?api=1&destination=${latitud},${longitud}`
}
