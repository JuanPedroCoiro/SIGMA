import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  obtenerProblemDetail,
  tipoProblema,
  TIPOS_PROBLEMA,
} from '../../../shared/api/problemDetail'
import { CLAVE_RESUMEN_INICIO } from '../../pendientes/hooks/useResumenInicio'
import {
  anularOrden,
  asignarOrdenes,
  cerrarOrden,
  iniciarOrden,
  listarFotosOrden,
  obtenerCargaPorCuadrilla,
  obtenerOrden,
  pausarOrden,
  reanudarOrden,
} from '../api/ordenesApi'
import type { FotoParaCierre } from '../fotosCierre'
import type { DatosAsignacion, DatosCierre, DatosPausa, OrdenTrabajoDetalle } from '../types'

export const CLAVE_ORDENES = 'ordenes'

export function useOrden(id: number) {
  return useQuery({ queryKey: [CLAVE_ORDENES, id], queryFn: () => obtenerOrden(id) })
}

/**
 * La respuesta trae la OT actualizada: se guarda como su detalle y se recargan las demás consultas
 * de órdenes (por ejemplo, el tablero).
 */
function useActualizarOrden(id: number) {
  const queryClient = useQueryClient()
  return (orden: OrdenTrabajoDetalle) => {
    queryClient.setQueryData([CLAVE_ORDENES, id], orden)
    return queryClient.invalidateQueries({
      queryKey: [CLAVE_ORDENES],
      predicate: (consulta) => consulta.queryKey[1] !== id,
    })
  }
}

export function usePausarOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: (datos: DatosPausa) => pausarOrden(id, datos),
    onSuccess: actualizar,
  })
}

export function useReanudarOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({ mutationFn: () => reanudarOrden(id), onSuccess: actualizar })
}

export function useAnularOrden(id: number) {
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: (motivo: string) => anularOrden(id, motivo),
    onSuccess: actualizar,
  })
}

/**
 * HU-05: asignar o reasignar varias OTs. Invalida todas las consultas de órdenes: el tablero, los
 * detalles y la carga por cuadrilla.
 */
export function useAsignarOrdenes() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (datos: DatosAsignacion) => asignarOrdenes(datos),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [CLAVE_ORDENES] }),
  })
}

/** HU-05 (3): la carga de cada cuadrilla para la fecha elegida, como referencia al asignar. */
export function useCargaPorCuadrilla(fecha: string) {
  return useQuery({
    queryKey: [CLAVE_ORDENES, 'carga-por-cuadrilla', fecha],
    queryFn: () => obtenerCargaPorCuadrilla(fecha),
    enabled: fecha !== '',
  })
}

/** Si el error es «ya está En proceso»: el reintento de un inicio que ya se hizo (HU-07). */
function yaEstabaEnProceso(error: unknown) {
  const problema = obtenerProblemDetail(error)
  return (
    tipoProblema(problema) === TIPOS_PROBLEMA.transicionInvalida &&
    problema?.estadoActual === 'EN_PROCESO'
  )
}

/**
 * CU-3 paso 2: Asignada → En proceso. Si ya estaba En proceso (el primer intento llegó pero se
 * perdió la respuesta, o lo inició un compañero), es un éxito: se trae la OT como está.
 */
export function useIniciarOrden(id: number) {
  const queryClient = useQueryClient()
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: async () => {
      try {
        return await iniciarOrden(id)
      } catch (error) {
        if (yaEstabaEnProceso(error)) return obtenerOrden(id)
        throw error
      }
    },
    onSuccess: (orden) =>
      Promise.all([
        actualizar(orden),
        queryClient.invalidateQueries({ queryKey: CLAVE_RESUMEN_INICIO }),
      ]),
  })
}

/** Lo que manda el formulario de cierre: los datos (con el idOperacion) y las fotos elegidas. */
export type EnvioCierre = {
  datos: DatosCierre
  fotos: readonly FotoParaCierre[]
}

/**
 * CU-3 pasos 6 a 10 con las fotos (HU-08). El idOperacion lo genera el formulario una vez y lo
 * repite en cada reintento. Al cerrar se recargan la OT, la bandeja, las fotos del cierre y el
 * resumen del inicio.
 */
export function useCerrarOrden(id: number) {
  const queryClient = useQueryClient()
  const actualizar = useActualizarOrden(id)
  return useMutation({
    mutationFn: ({ datos, fotos }: EnvioCierre) => cerrarOrden(id, datos, fotos),
    onSuccess: (orden) =>
      Promise.all([
        actualizar(orden),
        queryClient.invalidateQueries({ queryKey: [CLAVE_FOTOS_ORDEN, id] }),
        queryClient.invalidateQueries({ queryKey: CLAVE_RESUMEN_INICIO }),
      ]),
  })
}

/** Clave propia: después del cierre (#89) se invalida con queryKey [CLAVE_FOTOS_ORDEN, id]. */
export const CLAVE_FOTOS_ORDEN = 'fotos-orden'

/** Las fotos del cierre técnico de la OT (#90). */
export function useFotosOrden(id: number) {
  return useQuery({ queryKey: [CLAVE_FOTOS_ORDEN, id], queryFn: () => listarFotosOrden(id) })
}
