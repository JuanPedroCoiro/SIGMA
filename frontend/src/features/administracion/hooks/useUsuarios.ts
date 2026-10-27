import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cambiarEstadoUsuario,
  crearUsuario,
  listarUsuarios,
  modificarUsuario,
  obtenerUsuario,
  restablecerPassword,
} from '../api/usuariosApi'
import type { DatosUsuario } from '../types'

const CLAVE = 'usuarios'
const ORDEN = 'nombre,asc'

/** Listado paginado (CU-13c). Mantiene la página anterior a la vista mientras carga la nueva. */
export function useUsuarios(pagina: number) {
  return useQuery({
    queryKey: [CLAVE, 'lista', pagina],
    queryFn: () => listarUsuarios({ page: pagina, sort: ORDEN }),
    placeholderData: keepPreviousData,
  })
}

export function useUsuario(id: number) {
  return useQuery({ queryKey: [CLAVE, id], queryFn: () => obtenerUsuario(id) })
}

/** Después de cualquier cambio se recargan el listado y el detalle. */
function useInvalidarUsuarios() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: [CLAVE] })
}

export function useCrearUsuario() {
  const invalidar = useInvalidarUsuarios()
  return useMutation({ mutationFn: crearUsuario, onSuccess: invalidar })
}

export function useModificarUsuario(id: number) {
  const invalidar = useInvalidarUsuarios()
  return useMutation({
    mutationFn: (datos: DatosUsuario) => modificarUsuario(id, datos),
    onSuccess: invalidar,
  })
}

export function useRestablecerPassword(id: number) {
  const invalidar = useInvalidarUsuarios()
  return useMutation({
    mutationFn: (password: string) => restablecerPassword(id, password),
    onSuccess: invalidar,
  })
}

export function useCambiarEstadoUsuario(id: number) {
  const invalidar = useInvalidarUsuarios()
  return useMutation({
    mutationFn: (accion: 'activar' | 'desactivar') => cambiarEstadoUsuario(id, accion),
    onSuccess: invalidar,
  })
}
