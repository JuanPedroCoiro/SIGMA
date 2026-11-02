// Reflejan los DTOs del backend (#43); se escriben a mano hasta tener el OpenAPI.
import type { EstadoOT, MotivoPausa, Prioridad, TipoOT } from '../ot/types'

export const CRITERIOS_ORDEN = ['VENCIMIENTO', 'PRIORIDAD', 'CREACION'] as const

export type CriterioOrden = (typeof CRITERIOS_ORDEN)[number]

export const ETIQUETA_CRITERIO: Record<CriterioOrden, string> = {
  VENCIMIENTO: 'Vencimiento',
  PRIORIDAD: 'Prioridad',
  CREACION: 'Fecha de creación',
}

/** Una fila de GET /api/v1/pendientes/ordenes. */
export type OrdenPendiente = {
  id: number
  numero: string
  tipo: TipoOT
  estado: EstadoOT
  /** null en las preventivas. */
  prioridad: Prioridad | null
  /** null = «SLA sin definir» (o preventiva). */
  fechaLimite: string | null
  slaDefinido: boolean
  vencida: boolean
  codigoInstalacion: string
  nombreInstalacion: string
  /** null si no está asignada. */
  cuadrilla: string | null
  /** null en las preventivas. */
  numeroIncidente: string | null
  /** null si no está en pausa. */
  pausa: { motivo: MotivoPausa; detalle: string | null; inicio: string } | null
  creadoEn: string
}

/** Un elemento de GET /api/v1/pendientes/incidentes-en-espera. */
export type IncidenteEnEspera = {
  id: number
  numero: string
  prioridad: Prioridad
  codigoInstalacion: string
  nombreInstalacion: string
  descripcion: string
  tercero: string
  causa: string | null
  creadoEn: string
}
