import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import {
  erroresDeCampo,
  obtenerProblemDetail,
  tipoProblema,
  TIPOS_PROBLEMA,
} from '../api/problemDetail'

const MENSAJE_GENERICO = 'No se pudieron guardar los cambios. Volvé a intentar en unos minutos.'

/** Conflictos (409) que corresponden a un campo del formulario, con su mensaje por defecto. */
const CONFLICTOS_DE_CAMPO: Record<string, { campo: string; mensaje: string }> = {
  [TIPOS_PROBLEMA.correoDuplicado]: {
    campo: 'correo',
    mensaje: 'Ya existe un usuario con ese correo.',
  },
  [TIPOS_PROBLEMA.cuadrillaDuplicada]: {
    campo: 'nombre',
    mensaje: 'Ya existe una cuadrilla con ese nombre.',
  },
}

/**
 * Pone los errores del backend junto a cada campo del formulario: los de validación (400, con
 * `errores`) y los duplicados (correo, nombre de cuadrilla). Devuelve el mensaje general para lo
 * que no corresponde a un campo, o null si todo quedó en los campos.
 */
export function aplicarErroresDelServidor<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  campos: readonly Path<T>[],
): string | null {
  const problema = obtenerProblemDetail(error)
  if (!problema) return MENSAJE_GENERICO
  const esCampo = (nombre: string): nombre is Path<T> => campos.includes(nombre as Path<T>)

  const conflicto = CONFLICTOS_DE_CAMPO[tipoProblema(problema) ?? '']
  if (conflicto && esCampo(conflicto.campo)) {
    setError(conflicto.campo, { message: problema.detail ?? conflicto.mensaje })
    return null
  }

  const errores = erroresDeCampo(problema)
  const sinCampo = errores.filter((e) => !esCampo(e.campo))
  errores
    .filter((e) => esCampo(e.campo))
    .forEach((e) => {
      setError(e.campo as Path<T>, { message: e.mensaje })
    })
  if (errores.length > 0 && sinCampo.length === 0) return null
  if (sinCampo.length > 0) return sinCampo.map((e) => e.mensaje).join(' ')
  return problema.detail ?? MENSAJE_GENERICO
}
