import type { FieldErrors } from 'react-hook-form'
import type { CamposIncidente } from '../../esquemas'

/** Registro de un incidente en pasos (#69): Dónde, Qué pasó y Resumen, donde se confirma. */
export type NumeroPaso = 1 | 2 | 3

export type Campo = keyof CamposIncidente

export const NUMEROS_PASO: readonly NumeroPaso[] = [1, 2, 3]

export const TITULO_PASO: Record<NumeroPaso, string> = {
  1: 'Dónde',
  2: 'Qué pasó',
  3: 'Resumen',
}

/** Los títulos en orden, para IndicadorPasos. */
export const TITULOS_PASOS: readonly string[] = NUMEROS_PASO.map((numero) => TITULO_PASO[numero])

/** Los campos que se validan al pasar al siguiente paso. El resumen no tiene campos propios. */
export const CAMPOS_POR_PASO = {
  1: ['idInstalacion', 'origen'],
  2: ['idCategoriaFalla', 'prioridad', 'descripcion', 'esTercero', 'tercero', 'causa'],
} as const satisfies Record<1 | 2, readonly Campo[]>

export type PasoConCampos = keyof typeof CAMPOS_POR_PASO

export const CAMPOS: readonly Campo[] = [...CAMPOS_POR_PASO[1], ...CAMPOS_POR_PASO[2]]

/** El paso donde se carga un campo: ahí se lleva a quien registra si el backend lo rechaza. */
export function pasoDeCampo(campo: Campo): PasoConCampos {
  return (CAMPOS_POR_PASO[1] as readonly Campo[]).includes(campo) ? 1 : 2
}

/** El paso de la URL (`?paso=2`). Sin paso, o con uno que no existe, es el primero. */
export function leerPaso(valor: string | null): NumeroPaso {
  const numero = Number(valor)
  return numero === 2 || numero === 3 ? numero : 1
}

export function propsError(errors: FieldErrors<CamposIncidente>, campo: Campo) {
  return {
    'aria-invalid': errors[campo] ? true : undefined,
    'aria-describedby': errors[campo] ? `${campo}-error` : undefined,
  }
}
