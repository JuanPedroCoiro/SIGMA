// Reflejan los DTOs del backend (#39); se escriben a mano hasta tener el OpenAPI.

export const MOTIVOS_PAUSA = ['TERCERO', 'MATERIALES', 'CLIMA', 'FIN_DE_JORNADA', 'OTRO'] as const

export type MotivoPausa = (typeof MOTIVOS_PAUSA)[number]

export const ETIQUETA_MOTIVO: Record<MotivoPausa, string> = {
  TERCERO: 'Falla de un tercero',
  MATERIALES: 'Falta de materiales',
  CLIMA: 'Clima',
  FIN_DE_JORNADA: 'Fin de jornada',
  OTRO: 'Otro',
}

/** Motivos con detalle obligatorio (MotivoPausa.requiereDetalle en el backend). */
export const MOTIVOS_CON_DETALLE: readonly MotivoPausa[] = ['TERCERO', 'OTRO']

/** Solo un tercero reinicia el SLA (MotivoPausa.reiniciaSla en el backend). */
export const MOTIVO_QUE_REINICIA_SLA: MotivoPausa = 'TERCERO'

export type EstadoOT =
  'PENDIENTE' | 'ASIGNADA' | 'EN_PROCESO' | 'EN_PAUSA' | 'CERRADA_TECNICA' | 'VALIDADA' | 'ANULADA'

export const ETIQUETA_ESTADO_OT: Record<EstadoOT, string> = {
  PENDIENTE: 'Pendiente',
  ASIGNADA: 'Asignada',
  EN_PROCESO: 'En proceso',
  EN_PAUSA: 'En pausa',
  CERRADA_TECNICA: 'Cerrada técnicamente',
  VALIDADA: 'Validada',
  ANULADA: 'Anulada',
}

/** Estados desde los que se puede pausar (Fig. 18). */
export const ESTADOS_PAUSABLES: readonly EstadoOT[] = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO']

export type TipoOT = 'CORRECTIVA' | 'PREVENTIVA'

export type PausaOT = {
  motivo: MotivoPausa
  detalle: string | null
  inicio: string
  /** null mientras está abierta. */
  fin: string | null
  idUsuarioPausa: number
  idUsuarioReanudacion: number | null
}

/** En minutos. */
export type TiemposOT = {
  totalMinutos: number
  enPausaMinutos: Partial<Record<MotivoPausa, number>>
  efectivoMinutos: number
}

/** GET /api/v1/ordenes/{id}, y la respuesta de pausar y reanudar. */
export type OrdenTrabajoDetalle = {
  id: number
  numero: string
  tipo: TipoOT
  estado: EstadoOT
  idCuadrilla: number | null
  inicioSla: string | null
  /** null = «SLA sin definir» (o preventiva). */
  fechaLimite: string | null
  slaDefinido: boolean
  vencida: boolean
  pausaAbierta: PausaOT | null
  /** null si la OT ya no está abierta. */
  tiempos: TiemposOT | null
  pausas: PausaOT[]
}

/** POST /api/v1/ordenes/{id}/pausar. */
export type DatosPausa = {
  motivo: MotivoPausa
  detalle: string | null
}
