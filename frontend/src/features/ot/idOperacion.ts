/**
 * Identificador de operación del cierre técnico (R8, CU-3 7b): se genera una vez por cierre y se
 * repite en cada reintento. `crypto.randomUUID` solo existe en contextos seguros (https o
 * localhost); si el celular entra por http a una IP de la red, se arma un UUID v4 con
 * `crypto.getRandomValues`, que sí está.
 */
export function generarIdOperacion(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // versión 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variante RFC 9562
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
