import { cliente } from '../../../shared/api/cliente'
import type { PingResponse } from '../types'

export async function consultarPing(): Promise<PingResponse> {
  const res = await cliente.get<PingResponse>('/ping')
  return res.data
}
