import { cliente } from '../../../shared/api/cliente'
import type { ResumenInicio } from '../types'

/** Contadores de la página de inicio según el rol de quien pregunta (#72). */
export async function obtenerResumenInicio(): Promise<ResumenInicio> {
  const res = await cliente.get<ResumenInicio>('/inicio/resumen')
  return res.data
}
