import { useQuery } from '@tanstack/react-query'
import { consultarPing } from '../api/estadoApi'

export function usePing() {
  return useQuery({ queryKey: ['ping'], queryFn: consultarPing })
}
