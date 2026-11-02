import { z } from 'zod'
import { MOTIVOS_PAUSA, type MotivoPausa } from './types'

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
