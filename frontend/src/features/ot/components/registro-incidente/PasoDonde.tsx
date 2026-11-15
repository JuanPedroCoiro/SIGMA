import { Controller, useFormContext } from 'react-hook-form'
import { BuscadorInstalacion } from '../../../activos/components/BuscadorInstalacion'
import type { Instalacion } from '../../../activos/types'
import type { CamposIncidente } from '../../esquemas'
import { ETIQUETA_ORIGEN, ORIGENES_INCIDENTE } from '../../types'
import { MensajeError } from './MensajeError'
import { propsError } from './pasos'

type Props = {
  /** La instalación elegida: la guarda la página para el resumen y para volver a este paso. */
  instalacion: Instalacion | null
  onInstalacion: (instalacion: Instalacion | null) => void
}

/** Paso 1: la instalación y el origen del incidente. */
export function PasoDonde({ instalacion, onInstalacion }: Props) {
  const {
    control,
    register,
    formState: { errors },
  } = useFormContext<CamposIncidente>()

  return (
    <div className="registrar-incidente-donde">
      <div className="registrar-incidente-campos">
        <div className="campo">
          <label htmlFor="idInstalacion">Instalación</label>
          <Controller
            control={control}
            name="idInstalacion"
            render={({ field }) => (
              <BuscadorInstalacion
                id="idInstalacion"
                value={field.value === null ? null : instalacion}
                onChange={(elegida) => {
                  onInstalacion(elegida)
                  field.onChange(elegida?.id ?? null)
                }}
                onBlur={field.onBlur}
                inputRef={field.ref}
                invalido={Boolean(errors.idInstalacion)}
                idError={errors.idInstalacion ? 'idInstalacion-error' : undefined}
              />
            )}
          />
          <MensajeError campo="idInstalacion" />
        </div>

        <fieldset className="registrar-incidente-opciones" {...propsError(errors, 'origen')}>
          <legend>Origen</legend>
          {ORIGENES_INCIDENTE.map((origen) => (
            <label key={origen}>
              <input type="radio" value={origen} {...register('origen')} />
              {ETIQUETA_ORIGEN[origen]}
            </label>
          ))}
          <MensajeError campo="origen" />
        </fieldset>
      </div>
      {/*
        #70: el mapa va acá, como segunda columna de .registrar-incidente-donde (al costado en
        escritorio, debajo en el celular). Elegir en el mapa llama a onInstalacion y a setValue
        ('idInstalacion', id), igual que el buscador.
      */}
    </div>
  )
}
