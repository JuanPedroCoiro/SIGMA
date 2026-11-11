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
  /** Fecha de ejecución asignada («2026-11-12»); null si no está asignada. */
  fechaEjecucion: string | null
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

export type Prioridad = 'ALTA' | 'MEDIA' | 'BAJA'

export const ETIQUETA_PRIORIDAD: Record<Prioridad, string> = {
  ALTA: 'Alta',
  MEDIA: 'Media',
  BAJA: 'Baja',
}

export type EstadoIncidente = 'ABIERTO' | 'EN_ESPERA_TERCERO' | 'CON_OT' | 'CERRADO_SIN_OT'

/** Respuesta de POST /api/v1/incidentes (#40) y de /{id}/cerrar-sin-ot y /generar-ot (#38). */
export type IncidenteResponse = {
  id: number
  numero: string
  estado: EstadoIncidente
  prioridad: Prioridad
  tercero: string | null
  causa: string | null
  /** null si no tiene OT (en espera de tercero o cerrado sin OT). */
  ordenTrabajo: {
    id: number
    numero: string
    inicioSla: string
    fechaLimite: string | null
    slaDefinido: boolean
  } | null
}

export const MOTIVOS_ANULACION = ['DUPLICADA', 'IMPROCEDENTE', 'OTRO'] as const

export type MotivoAnulacion = (typeof MOTIVOS_ANULACION)[number]

export const ETIQUETA_MOTIVO_ANULACION: Record<MotivoAnulacion, string> = {
  DUPLICADA: 'Duplicada',
  IMPROCEDENTE: 'Improcedente',
  OTRO: 'Otro',
}

const ESTADOS_ANULABLES: readonly EstadoOT[] = ['PENDIENTE', 'ASIGNADA']

/**
 * Si la OT se puede anular (EstadoOT.anular en el backend, CU-19): desde Pendiente o Asignada, o
 * En pausa si antes estaba en uno de esos.
 */
export function puedeAnularse(estado: EstadoOT, estadoAnteriorPausa: EstadoOT | null) {
  if (estado === 'EN_PAUSA') {
    return estadoAnteriorPausa !== null && ESTADOS_ANULABLES.includes(estadoAnteriorPausa)
  }
  return ESTADOS_ANULABLES.includes(estado)
}

/** Desde qué estados se puede asignar o reasignar (Fig. 18): lo mismo que decide EstadoOT. */
const ESTADOS_ASIGNABLES: readonly EstadoOT[] = ['PENDIENTE', 'ASIGNADA']

export function puedeAsignarse(estado: EstadoOT) {
  return ESTADOS_ASIGNABLES.includes(estado)
}

/** POST /api/v1/ordenes/asignar (HU-05). */
export type DatosAsignacion = {
  idsOrdenTrabajo: number[]
  idCuadrilla: number
  /** «2026-11-12»: fecha sin hora. */
  fechaEjecucion: string
}

/** Un elemento de GET /api/v1/ordenes/carga-por-cuadrilla. Las que no vienen tienen 0. */
export type CargaCuadrilla = {
  idCuadrilla: number
  ordenes: number
}

/** Un elemento de GET /api/v1/categorias-falla (HU-03): solo las activas. */
export type CategoriaFalla = {
  id: number
  nombre: string
  prioridadSugerida: Prioridad
}

/** La categoría genérica (CategoriaFalla.OTRA en el backend): el detalle va en la descripción. */
export const CATEGORIA_OTRA = 'Otra'

/** Mínimo de la descripción con «Otra» (CategoriaFalla.MINIMO_DETALLE_OTRA en el backend). */
export const MINIMO_DETALLE_OTRA = 10

export function esCategoriaOtra(categoria: CategoriaFalla | undefined) {
  return categoria?.nombre.toLowerCase() === CATEGORIA_OTRA.toLowerCase()
}

export const ORIGENES_INCIDENTE = ['RECLAMO_ORGANISMO', 'MONITOREO'] as const

export type OrigenIncidente = (typeof ORIGENES_INCIDENTE)[number]

export const ETIQUETA_ORIGEN: Record<OrigenIncidente, string> = {
  RECLAMO_ORGANISMO: 'Reclamo de un organismo',
  MONITOREO: 'Detección en el monitoreo',
}

export const PRIORIDADES: readonly Prioridad[] = ['ALTA', 'MEDIA', 'BAJA']

/** POST /api/v1/incidentes (CU-1). Con `tercero`, queda en espera sin OT (4b). */
export type DatosIncidente = {
  idInstalacion: number
  origen: OrigenIncidente
  idCategoriaFalla: number
  prioridad: Prioridad
  descripcion: string
  tercero: string | null
  causa: string | null
}
