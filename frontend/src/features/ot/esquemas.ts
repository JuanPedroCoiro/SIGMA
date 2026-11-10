import { z } from 'zod'
import {
  ETIQUETA_MOTIVO_ANULACION,
  MOTIVOS_ANULACION,
  MOTIVOS_PAUSA,
  type MotivoPausa,
} from './types'

import { hoyEnMontevideo } from '../../shared/lib/fechas'

const MENSAJE_DETALLE: Partial<Record<MotivoPausa, string>> = {
  TERCERO: 'Indicá qué tercero causa la pausa.',
  OTRO: 'Detallá el motivo de la pausa.',
}

/** Las mismas reglas que PausarOtRequest y PausaOT: detalle obligatorio con TERCERO y OTRO. */
export const esquemaPausa = z
  .object({
    motivo: z.enum(MOTIVOS_PAUSA, { error: 'Elegí el motivo de la pausa.' }),
    detalle: z.string().trim().max(500, 'Máximo 500 caracteres.'),
  })
  .superRefine((datos, ctx) => {
    const mensaje = MENSAJE_DETALLE[datos.motivo]
    if (mensaje && !datos.detalle) {
      ctx.addIssue({ code: 'custom', path: ['detalle'], message: mensaje })
    }
  })
  .transform((datos) => ({ motivo: datos.motivo, detalle: datos.detalle || null }))

export type CamposPausa = z.input<typeof esquemaPausa>
export type ValoresPausa = z.output<typeof esquemaPausa>

/**
 * Anulación (CU-19): el backend recibe un texto. Se arma con el motivo elegido y el detalle
 * («Duplicada: misma falla que OT-0005»); con «Otro» el detalle es obligatorio.
 */
export const esquemaAnulacion = z
  .object({
    motivo: z.enum(MOTIVOS_ANULACION, { error: 'Elegí el motivo de la anulación.' }),
    detalle: z.string().trim().max(480, 'Máximo 480 caracteres.'),
  })
  .superRefine((datos, ctx) => {
    if (datos.motivo === 'OTRO' && !datos.detalle) {
      ctx.addIssue({ code: 'custom', path: ['detalle'], message: 'Detallá el motivo.' })
    }
  })
  .transform((datos) => {
    const etiqueta = ETIQUETA_MOTIVO_ANULACION[datos.motivo]
    return { motivo: datos.detalle ? `${etiqueta}: ${datos.detalle}` : etiqueta }
  })

/**
 * Asignación (HU-05): las mismas reglas que AsignarOtsRequest y AsignacionOtService. La fecha no
 * puede ser anterior a hoy en Montevideo (2a). El select entrega el id de la cuadrilla como texto.
 */
export const esquemaAsignacion = z
  .object({
    fechaEjecucion: z.string().min(1, 'Elegí la fecha de ejecución.'),
    idCuadrilla: z.string().min(1, 'Elegí la cuadrilla.'),
  })
  .superRefine((datos, ctx) => {
    if (datos.fechaEjecucion && datos.fechaEjecucion < hoyEnMontevideo()) {
      ctx.addIssue({
        code: 'custom',
        path: ['fechaEjecucion'],
        message: 'La fecha no puede ser anterior a hoy.',
      })
    }
  })
  .transform((datos) => ({
    fechaEjecucion: datos.fechaEjecucion,
    idCuadrilla: Number(datos.idCuadrilla),
  }))

export type CamposAsignacion = z.input<typeof esquemaAsignacion>
export type ValoresAsignacion = z.output<typeof esquemaAsignacion>
export type CamposAnulacion = z.input<typeof esquemaAnulacion>
export type ValoresAnulacion = z.output<typeof esquemaAnulacion>
