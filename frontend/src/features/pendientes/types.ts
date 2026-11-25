// Reflejan los DTOs del backend (#43); se escriben a mano hasta tener el OpenAPI.
import type { EstadoOT, MotivoPausa, Prioridad, TipoOT } from '../ot/types'

export const CRITERIOS_ORDEN = ['VENCIMIENTO', 'PRIORIDAD', 'CREACION'] as const

export type CriterioOrden = (typeof CRITERIOS_ORDEN)[number]

export type EstadoTablero = (typeof ESTADOS_TABLERO)[number]

export const ETIQUETA_CRITERIO: Record<CriterioOrden, string> = {
  VENCIMIENTO: 'Vencimiento',
  PRIORIDAD: 'Prioridad',
  CREACION: 'Fecha de creación',
}

/** Estados que muestra el tablero, para filtrar (?estado=PENDIENTE, #72). */
export const ESTADOS_TABLERO = [
  'PENDIENTE',
  'ASIGNADA',
  'EN_PROCESO',
  'EN_PAUSA',
] as const satisfies readonly EstadoOT[]

/** Una fila de GET /api/v1/pendientes/ordenes. */
export type OrdenPendiente = {
  id: number
  numero: string
  tipo: TipoOT
  estado: EstadoOT
  /** Solo en EN_PAUSA: decide si se puede anular (CU-19). */
  estadoAnteriorPausa: EstadoOT | null
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

/**
 * Un elemento de GET /api/v1/mis-ots (HU-07): las OTs Asignada, En proceso y En pausa de la
 * cuadrilla del técnico. La dirección, la ruta y las coordenadas son para llegar desde el celular.
 */
export type OrdenDeMiCuadrilla = {
  id: number
  numero: string
  tipo: TipoOT
  estado: EstadoOT
  /** Solo en EN_PAUSA. */
  estadoAnteriorPausa: EstadoOT | null
  /** null en las preventivas. */
  prioridad: Prioridad | null
  /** Nombre de la categoría de falla; null en las preventivas. */
  categoria: string | null
  /** null en las preventivas. */
  numeroIncidente: string | null
  /** «2026-11-12»; null si no tiene. */
  fechaEjecucion: string | null
  /** null = «SLA sin definir» (o preventiva). */
  fechaLimite: string | null
  slaDefinido: boolean
  vencida: boolean
  /** null si todavía no empezó. */
  iniciadaEn: string | null
  instalacion: InstalacionDeMiOt
  /** null si no está en pausa. */
  pausa: { motivo: MotivoPausa; detalle: string | null; inicio: string } | null
}

export type InstalacionDeMiOt = {
  id: number
  codigo: string
  nombre: string
  direccion: string | null
  rutaZona: string | null
  /** null si la instalación no tiene coordenadas cargadas. */
  latitud: number | null
  longitud: number | null
}

/** GET /api/v1/inicio/resumen (#72): null en lo que no corresponde al rol. */
export type ResumenInicio = {
  pendientesDeAsignar: number | null
  vencidas: number | null
  porVencer: number | null
  enPausa: number | null
  incidentesEnEspera: number | null
  incidentesHoy: number | null
  otsDeHoy: number | null
}
