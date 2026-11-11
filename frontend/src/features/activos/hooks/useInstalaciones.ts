import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  buscarInstalaciones,
  importarInstalaciones,
  listarInstalaciones,
} from '../api/instalacionesApi'

const CLAVE = 'instalaciones'
const ORDEN = 'codigo,asc'

/** Mínimo de caracteres para buscar: con menos, coincidirían casi todas. */
export const MINIMO_BUSQUEDA = 2

/** Listado paginado (HU-02). Mantiene la página anterior a la vista mientras carga la nueva. */
export function useInstalaciones(pagina: number) {
  return useQuery({
    queryKey: [CLAVE, 'lista', pagina],
    queryFn: () => listarInstalaciones({ page: pagina, sort: ORDEN }),
    placeholderData: keepPreviousData,
  })
}

/** Búsqueda por código o nombre (HU-03); no pide nada hasta tener MINIMO_BUSQUEDA caracteres. */
export function useBuscarInstalaciones(texto: string) {
  const buscar = texto.trim()
  return useQuery({
    queryKey: [CLAVE, 'busqueda', buscar],
    queryFn: () => buscarInstalaciones(buscar),
    enabled: buscar.length >= MINIMO_BUSQUEDA,
    placeholderData: keepPreviousData,
  })
}

/** Al importar se recarga el listado. */
export function useImportarInstalaciones() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: importarInstalaciones,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [CLAVE] }),
  })
}
