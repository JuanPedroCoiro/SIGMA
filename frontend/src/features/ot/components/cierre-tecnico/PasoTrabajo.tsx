import { useFormContext } from 'react-hook-form'
import type { CamposCierre } from '../../esquemas'
import { propsError, type Campo } from './pasos'

const CAMPOS: { campo: Campo; etiqueta: string; ayuda: string; obligatorio: boolean }[] = [
  { campo: 'diagnostico', etiqueta: 'Diagnóstico', ayuda: 'Qué encontraste.', obligatorio: true },
  {
    campo: 'solucion',
    etiqueta: 'Solución',
    ayuda: 'Qué hiciste para resolverlo.',
    obligatorio: true,
  },
  { campo: 'observaciones', etiqueta: 'Observaciones', ayuda: 'Opcional.', obligatorio: false },
]

/** Paso 1 del cierre (CU-3 paso 6): diagnóstico y solución obligatorios, observaciones opcionales. */
export function PasoTrabajo() {
  const {
    register,
    formState: { errors },
  } = useFormContext<CamposCierre>()

  return (
    <div className="cierre-campos">
      {CAMPOS.map(({ campo, etiqueta, ayuda, obligatorio }) => {
        const id = `cierre-${campo}`
        const error = errors[campo]?.message
        return (
          <div key={campo} className="campo">
            <label htmlFor={id}>{etiqueta}</label>
            <textarea
              id={id}
              rows={4}
              aria-required={obligatorio || undefined}
              {...propsError(errors, campo)}
              {...register(campo)}
            />
            {error ? (
              <span id={`${id}-error`} className="campo-error">
                {error}
              </span>
            ) : (
              <span id={`${id}-ayuda`} className="campo-ayuda">
                {ayuda}
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}
