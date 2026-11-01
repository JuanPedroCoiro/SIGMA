import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cambiarEstadoCuadrilla,
  crearCuadrilla,
  listarCuadrillas,
  modificarCuadrilla,
} from '../api/cuadrillasApi'
import type { DatosCuadrilla } from '../types'

const CLAVE = 'cuadrillas'

/** Catálogo de cuadrillas; cambia poco, así que se guarda en caché unos minutos. */
export function useCuadrillas() {
  return useQuery({ queryKey: [CLAVE], queryFn: listarCuadrillas, staleTime: 5 * 60_000 })
}

function useInvalidarCuadrillas() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [CLAVE] })
}

export function useCrearCuadrilla() {
  const invalidar = useInvalidarCuadrillas()
  return useMutation({ mutationFn: crearCuadrilla, onSuccess: invalidar })
}

export function useModificarCuadrilla(id: number) {
  const invalidar = useInvalidarCuadrillas()
  return useMutation({
    mutationFn: (datos: DatosCuadrilla) => modificarCuadrilla(id, datos),
    onSuccess: invalidar,
  })
}

type CambioEstado = { id: number; accion: 'activar' | 'desactivar' }

export function useCambiarEstadoCuadrilla() {
  const invalidar = useInvalidarCuadrillas()
  return useMutation({
    mutationFn: ({ id, accion }: CambioEstado) => cambiarEstadoCuadrilla(id, accion),
    onSuccess: invalidar,
  })
}
