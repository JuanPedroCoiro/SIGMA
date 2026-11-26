import { useState } from 'react'
import { obtenerProblemDetail } from '../../../shared/api/problemDetail'
import { Modal } from '../../../shared/components/Modal'
import { useCambiarCuadrilla } from '../hooks/useMiCuadrilla'
import type { MiCuadrilla } from '../types'
import './CambiarCuadrillaDialogo.css'

type Props = {
  miCuadrilla: MiCuadrilla
  onCerrar: () => void
  /** Después del cambio, con el nombre de la cuadrilla nueva. */
  onCambiada: (nombre: string) => void
}

/**
 * El técnico se suma a otra cuadrilla activa, o vuelve a la suya (HU-20). Si la actual tiene OTs,
 * avisa cuántas va a dejar de ver en «Mis OTs». Pensado para el celular: filas grandes para tocar.
 */
export function CambiarCuadrillaDialogo({ miCuadrilla, onCerrar, onCambiada }: Props) {
  const cambiar = useCambiarCuadrilla()
  const idActual = miCuadrilla.actual?.id ?? null
  const [elegida, setElegida] = useState<number | null>(idActual)
  const [error, setError] = useState<string | null>(null)
  const aviso = textoAviso(miCuadrilla)

  async function confirmar() {
    if (elegida === null) return
    setError(null)
    try {
      const nueva = await cambiar.mutateAsync(elegida)
      onCambiada(nueva.actual?.nombre ?? '')
    } catch (e) {
      setError(
        obtenerProblemDetail(e)?.detail ?? 'No se pudo cambiar de cuadrilla. Volvé a intentar.',
      )
    }
  }

  return (
    <Modal titulo="Cambiar de cuadrilla" onCerrar={onCerrar}>
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
      {aviso && <p className="alerta alerta-aviso">{aviso}</p>}
      <fieldset className="cambiar-cuadrilla-opciones">
        <legend>¿A qué cuadrilla te sumás?</legend>
        {miCuadrilla.activas.map((c) => (
          <label key={c.id} className="cambiar-cuadrilla-opcion">
            <input
              type="radio"
              name="cuadrilla"
              value={c.id}
              checked={elegida === c.id}
              onChange={() => setElegida(c.id)}
            />
            <span>
              {c.nombre}
              {c.id === idActual && ' (tu cuadrilla actual)'}
              {c.zona && <small>{c.zona}</small>}
            </span>
          </label>
        ))}
      </fieldset>
      <div className="modal-acciones">
        <button type="button" className="boton boton-secundario" onClick={onCerrar}>
          Cancelar
        </button>
        <button
          type="button"
          className="boton"
          onClick={confirmar}
          disabled={elegida === null || elegida === idActual || cambiar.isPending}
        >
          {cambiar.isPending ? 'Cambiando...' : 'Cambiarme'}
        </button>
      </div>
    </Modal>
  )
}

/**
 * «Vas a dejar de ver 3 OTs de tu cuadrilla en Mis OTs (1 en proceso o en pausa).»; null si no
 * tiene ninguna.
 */
function textoAviso({ otsAbiertas, otsEnCurso }: MiCuadrilla) {
  if (otsAbiertas === 0) return null
  const ots = otsAbiertas === 1 ? '1 OT' : `${otsAbiertas} OTs`
  const enCurso = otsEnCurso > 0 ? ` (${otsEnCurso} en proceso o en pausa)` : ''
  return `Vas a dejar de ver ${ots} de tu cuadrilla en Mis OTs${enCurso}.`
}
