import { cliente } from '../../../shared/api/cliente'
import type { MiCuadrilla } from '../types'

/** La cuadrilla del técnico, cuántas OTs dejaría de ver al cambiarse y las activas (HU-20). */
export async function obtenerMiCuadrilla(): Promise<MiCuadrilla> {
  const res = await cliente.get<MiCuadrilla>('/mi-cuadrilla')
  return res.data
}

/** Se suma a otra cuadrilla activa o vuelve a la suya. Responde como el GET, ya actualizado. */
export async function cambiarMiCuadrilla(idCuadrilla: number): Promise<MiCuadrilla> {
  const res = await cliente.post<MiCuadrilla>('/mi-cuadrilla/cambiar', { idCuadrilla })
  return res.data
}
