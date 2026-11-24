import { useState } from 'react'
import { useIniciarOrden } from '../hooks/useOrden'
import { mensajeErrorEjecucion } from '../mensajesEjecucion'
import type { OrdenTrabajoDetalle } from '../types'
import './BotonIniciar.css'

type Props = {
  orden: Pick<OrdenTrabajoDetalle, 'id' | 'numero'>
}

/**
 * «Iniciar intervención» (CU-3 paso 2) para una OT Asignada: en la tarjeta de la bandeja y en la
 * intervención (HU-07). Si ya estaba En proceso, useIniciarOrden lo toma como éxito.
 */
export function BotonIniciar({ orden }: Props) {
  const iniciar = useIniciarOrden(orden.id)
  const [error, setError] = useState<string | null>(null)

  async function alIniciar() {
    setError(null)
    try {
      await iniciar.mutateAsync()
    } catch (e) {
      setError(mensajeErrorEjecucion(e).mensaje)
    }
  }

  return (
    <div className="boton-iniciar">
      {error && (
        <p className="alerta alerta-error" role="alert">
          {error}
        </p>
      )}
      <button
        type="button"
        className="boton boton-primario boton-grande"
        aria-label={`Iniciar intervención de ${orden.numero}`}
        disabled={iniciar.isPending}
        onClick={alIniciar}
      >
        {iniciar.isPending ? 'Iniciando...' : 'Iniciar intervención'}
      </button>
    </div>
  )
}
