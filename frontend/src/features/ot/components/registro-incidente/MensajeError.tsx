import { useFormContext } from 'react-hook-form'
import type { CamposIncidente } from '../../esquemas'
import type { Campo } from './pasos'

/** El mensaje de error de un campo, con el id al que apunta su aria-describedby. */
export function MensajeError({ campo }: { campo: Campo }) {
  const {
    formState: { errors },
  } = useFormContext<CamposIncidente>()
  const mensaje = errors[campo]?.message
  return mensaje ? (
    <span id={`${campo}-error`} className="campo-error">
      {mensaje}
    </span>
  ) : null
}
