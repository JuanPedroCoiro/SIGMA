import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { Modal } from '../../../shared/components/Modal'
import { aplicarErroresDelServidor } from '../../../shared/lib/erroresFormulario'
import { esquemaPausa, type CamposPausa, type ValoresPausa } from '../esquemas'
import { usePausarOrden, useReanudarOrden } from '../hooks/useOrden'
import {
  ESTADOS_PAUSABLES,
  ETIQUETA_MOTIVO,
  MOTIVO_QUE_REINICIA_SLA,
  MOTIVOS_CON_DETALLE,
  MOTIVOS_PAUSA,
  type OrdenTrabajoDetalle,
} from '../types'
import './AccionesPausa.css'

type Props = {
  orden: Pick<OrdenTrabajoDetalle, 'id' | 'numero' | 'estado'>
}

/**
 * «Pausar» (con motivo y detalle) o «Reanudar», según el estado de la OT. Se usa en el detalle de
 * la OT y en el tablero (#43). Si la OT no se puede pausar ni reanudar, no muestra nada.
 */
export function AccionesPausa({ orden }: Props) {
  const reanudar = useReanudarOrden(orden.id)
  const [dialogoAbierto, setDialogoAbierto] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enPausa = orden.estado === 'EN_PAUSA'
  if (!enPausa && !ESTADOS_PAUSABLES.includes(orden.estado)) return null

  async function alReanudar() {
    setError(null)
    try {
      await reanudar.mutateAsync()
    } catch (e) {
      setError(obtenerProblemDetail(e)?.detail ?? 'No se pudo reanudar la OT. Volvé a intentar.')
    }
  }

  return (
    <div className="acciones-pausa">
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
      {enPausa ? (
        <button
          type="button"
          className="boton boton-primario"
          disabled={reanudar.isPending}
          onClick={alReanudar}
        >
          {reanudar.isPending ? 'Reanudando...' : 'Reanudar'}
        </button>
      ) : (
        <button
          type="button"
          className="boton boton-secundario"
          onClick={() => {
            setError(null)
            setDialogoAbierto(true)
          }}
        >
          Pausar
        </button>
      )}
      {dialogoAbierto && <DialogoPausa orden={orden} onCerrar={() => setDialogoAbierto(false)} />}
    </div>
  )
}

type PropsDialogo = {
  orden: Pick<OrdenTrabajoDetalle, 'id' | 'numero'>
  onCerrar: () => void
}

function DialogoPausa({ orden, onCerrar }: PropsDialogo) {
  const pausar = usePausarOrden(orden.id)
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CamposPausa, unknown, ValoresPausa>({
    resolver: zodResolver(esquemaPausa),
    defaultValues: { detalle: '' },
  })
  const motivo = useWatch({ control, name: 'motivo' })
  const detalleObligatorio = MOTIVOS_CON_DETALLE.includes(motivo)

  async function enviar(valores: ValoresPausa) {
    setErrorGeneral(null)
    try {
      await pausar.mutateAsync(valores)
      onCerrar()
    } catch (error) {
      setErrorGeneral(aplicarErroresDelServidor(error, setError, ['motivo', 'detalle']))
    }
  }

  return (
    <Modal titulo={`Pausar ${orden.numero}`} onCerrar={onCerrar}>
      <form className="formulario-pausa" noValidate onSubmit={handleSubmit(enviar)}>
        {errorGeneral && (
          <p className="alerta alerta-error" role="alert">
            {errorGeneral}
          </p>
        )}
        <div className="campo">
          <label htmlFor="pausa-motivo">Motivo</label>
          <select
            id="pausa-motivo"
            aria-invalid={errors.motivo ? true : undefined}
            aria-describedby={errors.motivo ? 'pausa-motivo-error' : undefined}
            {...register('motivo')}
          >
            <option value="">Elegí un motivo</option>
            {MOTIVOS_PAUSA.map((m) => (
              <option key={m} value={m}>
                {ETIQUETA_MOTIVO[m]}
              </option>
            ))}
          </select>
          {errors.motivo && (
            <span id="pausa-motivo-error" className="campo-error">
              {errors.motivo.message}
            </span>
          )}
          {motivo === MOTIVO_QUE_REINICIA_SLA && (
            <span className="campo-ayuda">
              El plazo del SLA vuelve a empezar completo al reanudar.
            </span>
          )}
        </div>
        <div className="campo">
          <label htmlFor="pausa-detalle">Detalle</label>
          <textarea
            id="pausa-detalle"
            rows={3}
            aria-required={detalleObligatorio}
            aria-invalid={errors.detalle ? true : undefined}
            aria-describedby={errors.detalle ? 'pausa-detalle-error' : 'pausa-detalle-ayuda'}
            {...register('detalle')}
          />
          <span id="pausa-detalle-ayuda" className="campo-ayuda">
            {motivo === 'TERCERO'
              ? 'Obligatorio: qué tercero (por ejemplo, UTE).'
              : detalleObligatorio
                ? 'Obligatorio.'
                : 'Opcional.'}
          </span>
          {errors.detalle && (
            <span id="pausa-detalle-error" className="campo-error">
              {errors.detalle.message}
            </span>
          )}
        </div>
        <div className="modal-acciones">
          <button type="button" className="boton boton-secundario" onClick={onCerrar}>
            Cancelar
          </button>
          <button type="submit" className="boton boton-primario" disabled={isSubmitting}>
            {isSubmitting ? 'Pausando...' : 'Confirmar pausa'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
