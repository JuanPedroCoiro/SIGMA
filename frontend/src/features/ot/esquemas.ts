import { z } from 'zod'
import {
  esCategoriaOtra,
  ETIQUETA_MOTIVO_ANULACION,
  MINIMO_DETALLE_OTRA,
  MOTIVOS_ANULACION,
  MOTIVOS_PAUSA,
  ORIGENES_INCIDENTE,
  type CategoriaFalla,
  type DatosIncidente,
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

/**
 * Por defecto zod no corre las reglas entre campos si algún campo ya falló; así, al enviar el
 * formulario vacío, también se marcan la instalación y el tercero. Las reglas toleran campos
 * inválidos.
 */
const CON_LOS_DEMAS_ERRORES = { when: () => true }

/**
 * Registro de un incidente (CU-1, HU-03): las mismas reglas que RegistrarIncidenteRequest. Con
 * «La falla es de un tercero», el tercero es obligatorio y la causa no (4b). Con la categoría
 * «Otra», la descripción tiene que detallar la falla (4a): por eso el esquema recibe el catálogo.
 */
export function crearEsquemaIncidente(categorias: readonly CategoriaFalla[]) {
  return z
    .object({
      idInstalacion: z.number({ error: 'Elegí la instalación.' }).nullable(),
      origen: z.enum(ORIGENES_INCIDENTE, { error: 'Indicá el origen.' }),
      idCategoriaFalla: z.string().min(1, 'Elegí la categoría de la falla.'),
      prioridad: z.enum(['ALTA', 'MEDIA', 'BAJA'], { error: 'Elegí la prioridad.' }),
      descripcion: z
        .string()
        .trim()
        .min(1, 'Describí la falla.')
        .max(2000, 'La descripción admite hasta 2000 caracteres.'),
      esTercero: z.boolean(),
      tercero: z.string().trim().max(150, 'El tercero admite hasta 150 caracteres.'),
      causa: z.string().trim().max(500, 'La causa admite hasta 500 caracteres.'),
    })
    .superRefine((datos, ctx) => {
      if (datos.idInstalacion === null) {
        ctx.addIssue({ code: 'custom', path: ['idInstalacion'], message: 'Elegí la instalación.' })
      }
      if (datos.esTercero && !datos.tercero) {
        ctx.addIssue({
          code: 'custom',
          path: ['tercero'],
          message: 'Indicá el tercero responsable de la falla.',
        })
      }
      const categoria = categorias.find((c) => String(c.id) === datos.idCategoriaFalla)
      if (
        esCategoriaOtra(categoria) &&
        datos.descripcion &&
        datos.descripcion.length < MINIMO_DETALLE_OTRA
      ) {
        ctx.addIssue({
          code: 'custom',
          path: ['descripcion'],
          message: `Con la categoría «Otra», detallá la falla en la descripción (al menos ${MINIMO_DETALLE_OTRA} caracteres).`,
        })
      }
    }, CON_LOS_DEMAS_ERRORES)
    .transform((datos): DatosIncidente => ({
      idInstalacion: datos.idInstalacion!,
      origen: datos.origen,
      idCategoriaFalla: Number(datos.idCategoriaFalla),
      prioridad: datos.prioridad,
      descripcion: datos.descripcion,
      tercero: datos.esTercero ? datos.tercero : null,
      causa: datos.esTercero && datos.causa ? datos.causa : null,
    }))
}

type EsquemaIncidente = ReturnType<typeof crearEsquemaIncidente>
export type CamposIncidente = z.input<EsquemaIncidente>
export type ValoresIncidente = z.output<EsquemaIncidente>
