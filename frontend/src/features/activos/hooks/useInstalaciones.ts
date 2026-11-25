import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  buscarInstalaciones,
  importarInstalaciones,
  listarInstalaciones,
  listarInstalacionesMapa,
  obtenerFichaInstalacion,
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

/** Las activas para el mapa (#70). Cambian poco: se reusan 5 minutos sin volver a pedirlas. */
export function useInstalacionesMapa() {
  return useQuery({
    queryKey: [CLAVE, 'mapa'],
    queryFn: listarInstalacionesMapa,
    staleTime: 5 * 60 * 1000,
  })
}

/** Ficha de la instalación (HU-10): las OTs en curso cambian, así que no se reusa como el mapa. */
export function useFichaInstalacion(id: number) {
  return useQuery({
    queryKey: [CLAVE, 'ficha', id],
    queryFn: () => obtenerFichaInstalacion(id),
  })
}

/** Al importar se recarga el listado (y el mapa). */
export function useImportarInstalaciones() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: importarInstalaciones,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [CLAVE] }),
  })
}
