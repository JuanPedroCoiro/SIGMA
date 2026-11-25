import type { FieldErrors } from 'react-hook-form'
import type { CamposCierre } from '../../esquemas'

/**
 * Cierre técnico en pasos (HU-07), como el registro de incidentes (#69), en el orden de CU-3:
 * Trabajo (paso 6), Fotos (paso 7) y Revisar y enviar, donde se confirma.
 */
export type NumeroPaso = 1 | 2 | 3

export type Campo = keyof CamposCierre

export const NUMEROS_PASO: readonly NumeroPaso[] = [1, 2, 3]

export const TITULO_PASO: Record<NumeroPaso, string> = {
  1: 'Trabajo',
  2: 'Fotos',
  3: 'Revisar y enviar',
}

/** Los títulos en orden, para IndicadorPasos. */
export const TITULOS_PASOS: readonly string[] = NUMEROS_PASO.map((numero) => TITULO_PASO[numero])

/**
 * Los campos de texto se cargan todos en el paso 1: ahí se lleva al técnico si el backend rechaza
 * alguno. Las fotos (paso 2) no son campos del formulario: un rechazo de fotos lleva al paso 2.
 */
export const CAMPOS: readonly Campo[] = ['diagnostico', 'solucion', 'observaciones']

export const PASO_DE_LOS_CAMPOS: NumeroPaso = 1
export const PASO_DE_LAS_FOTOS: NumeroPaso = 2

/** El paso de la URL (`?paso=2`). Sin paso, o con uno que no existe, es el primero. */
export function leerPaso(valor: string | null): NumeroPaso {
  const numero = Number(valor)
  return numero === 2 || numero === 3 ? numero : 1
}

export function propsError(errors: FieldErrors<CamposCierre>, campo: Campo) {
  return {
    'aria-invalid': errors[campo] ? true : undefined,
    'aria-describedby': errors[campo] ? `cierre-${campo}-error` : `cierre-${campo}-ayuda`,
  }
}
