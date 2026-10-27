import { useQuery } from '@tanstack/react-query'
import { listarCuadrillas } from '../api/cuadrillasApi'

/** Catálogo de cuadrillas; cambia poco, así que se guarda en caché unos minutos. */
export function useCuadrillas() {
  return useQuery({ queryKey: ['cuadrillas'], queryFn: listarCuadrillas, staleTime: 5 * 60_000 })
}
