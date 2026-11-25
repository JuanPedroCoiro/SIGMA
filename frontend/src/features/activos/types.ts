// Reflejan los DTOs del backend (#31); se escriben a mano hasta tener el OpenAPI.
import type { EstadoOT, Prioridad, TipoOT } from '../ot/types'

export type Instalacion = {
  id: number
  codigo: string
  nombre: string
  rutaZona: string
  latitud: number
  longitud: number
}

/** Error de una fila de la planilla; `fila` es la línea del archivo, contando el encabezado. */
export type ErrorFila = {
  fila: number
  campo: string
  mensaje: string
}

/** POST /api/v1/instalaciones/importar: 200 solo si no hubo errores (y entonces se importó todo). */
export type ResultadoImportacion = {
  importadas: number
  errores: ErrorFila[]
}

/** Una OT Pendiente, Asignada, En proceso o En pausa de la instalación (HU-10). */
export type OrdenEnCursoFicha = {
  id: number
  numero: string
  tipo: TipoOT
  estado: EstadoOT
  /** null si es preventiva. */
  prioridad: Prioridad | null
  fechaLimite: string | null
  slaDefinido: boolean
  vencida: boolean
  /** null si no está asignada. */
  cuadrilla: { id: number; nombre: string } | null
  /** null si es preventiva. */
  numeroIncidente: string | null
  creadoEn: string
  /** false para un técnico si la OT no es de su cuadrilla: el detalle respondería 403. */
  puedeVerDetalle: boolean
}

/**
 * GET /api/v1/instalaciones/{id}/ficha (HU-10, CU-7), para todos los roles. `tipo` y `direccion`
 * son null mientras no se carguen (2b). Las OTs en curso vienen primero las que vencen antes.
 */
export type FichaInstalacion = Instalacion & {
  tipo: string | null
  direccion: string | null
  activa: boolean
  organismo: { id: number; nombre: string }
  ordenesEnCurso: OrdenEnCursoFicha[]
}
