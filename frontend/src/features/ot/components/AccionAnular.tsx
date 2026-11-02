import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useSesion } from '../../../app/sesion/useSesion'
import { Modal } from '../../../shared/components/Modal'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaAnulacion, type CamposAnulacion, type ValoresAnulacion } from '../esquemas'
import { useAnularOrden } from '../hooks/useOrden'
import {
  ETIQUETA_MOTIVO_ANULACION,
  MOTIVOS_ANULACION,
  puedeAnularse,
  type EstadoOT,
} from '../types'

type Orden = {
  id: number
  numero: string
  estado: EstadoOT
  estadoAnteriorPausa: EstadoOT | null
}

/**
 * Botón «Anular» con el diálogo del motivo (CU-19, HU-04). Solo para la Jefatura y si la OT se
 * puede anular; si no, no muestra nada.
 */
export function AccionAnular({ orden }: { orden: Orden }) {
  const sesion = useSesion()
  const [abierto, setAbierto] = useState(false)
  const esJefatura = sesion.estado === 'autenticado' && sesion.usuario.rol === 'JEFATURA'
  if (!esJefatura || !puedeAnularse(orden.estado, orden.estadoAnteriorPausa)) return null

  return (
    <>
      <button type="button" className="boton boton-peligro" onClick={() => setAbierto(true)}>
        Anular
      </button>
      {abierto && <DialogoAnulacion orden={orden} onCerrar={() => setAbierto(false)} />}
    </>
  )
}

function DialogoAnulacion({ orden, onCerrar }: { orden: Orden; onCerrar: () => void }) {
  const anular = useAnularOrden(orden.id)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CamposAnulacion, unknown, ValoresAnulacion>({
    resolver: zodResolver(esquemaAnulacion),
    defaultValues: { detalle: '' },
  })
  const motivo = useWatch({ control, name: 'motivo' })

  async function enviar(valores: ValoresAnulacion) {
    setErrorGeneral(null)
    try {
      await anular.mutateAsync(valores.motivo)
      onCerrar()
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, ['motivo']))
    }
  }

  return (
    <Modal titulo={`Anular ${orden.numero}`} onCerrar={onCerrar}>
      <form noValidate onSubmit={handleSubmit(enviar)}>
        <p>La OT sale del tablero y queda constancia del motivo. No se puede deshacer.</p>
        {errorGeneral && (
          <p className="alerta alerta-error" role="alert">
            {errorGeneral}
          </p>
        )}
        <div className="campo">
          <label htmlFor="anulacion-motivo">Motivo</label>
          <select
            id="anulacion-motivo"
            aria-invalid={errors.motivo ? true : undefined}
            aria-describedby={errors.motivo ? 'anulacion-motivo-error' : undefined}
            {...register('motivo')}
          >
            <option value="">Elegí un motivo</option>
            {MOTIVOS_ANULACION.map((m) => (
              <option key={m} value={m}>
                {ETIQUETA_MOTIVO_ANULACION[m]}
              </option>
            ))}
          </select>
          {errors.motivo && (
            <span id="anulacion-motivo-error" className="campo-error">
              {errors.motivo.message}
            </span>
          )}
        </div>
        <div className="campo">
          <label htmlFor="anulacion-detalle">Detalle</label>
          <textarea
            id="anulacion-detalle"
            rows={3}
            aria-required={motivo === 'OTRO'}
            aria-invalid={errors.detalle ? true : undefined}
            aria-describedby={
              errors.detalle ? 'anulacion-detalle-error' : 'anulacion-detalle-ayuda'
            }
            {...register('detalle')}
          />
          <span id="anulacion-detalle-ayuda" className="campo-ayuda">
            {motivo === 'OTRO' ? 'Obligatorio.' : 'Opcional (por ejemplo, de qué OT es duplicada).'}
          </span>
          {errors.detalle && (
            <span id="anulacion-detalle-error" className="campo-error">
              {errors.detalle.message}
            </span>
          )}
        </div>
        <div className="modal-acciones">
          <button type="button" className="boton boton-secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="boton boton-peligro" disabled={isSubmitting}>
            {isSubmitting ? 'Anulando...' : 'Confirmar anulación'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
