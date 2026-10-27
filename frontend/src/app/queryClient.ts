import { QueryClient } from '@tanstack/react-query'
import { isAxiosError } from 'axios'

const MAX_REINTENTOS = 2

// Reintenta solo los errores de red y los 5xx: un 4xx no cambia por repetir el pedido.
function reintentar(fallas: number, error: unknown) {
  if (fallas >= MAX_REINTENTOS) return false
  if (!isAxiosError(error)) return false
  const status = error.response?.status
  return status === undefined || status >= 500
}

export function crearQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: reintentar,
      },
      mutations: {
        retry: false,
      },
    },
  })
}
