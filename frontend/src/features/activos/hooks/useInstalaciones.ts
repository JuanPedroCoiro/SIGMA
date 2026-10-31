import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { importarInstalaciones, listarInstalaciones } from '../api/instalacionesApi'

const CLAVE = 'instalaciones'
const ORDEN = 'codigo,asc'

/** Listado paginado (HU-02). Mantiene la página anterior a la vista mientras carga la nueva. */
export function useInstalaciones(pagina: number) {
  return useQuery({
    queryKey: [CLAVE, 'lista', pagina],
    queryFn: () => listarInstalaciones({ page: pagina, sort: ORDEN }),
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
