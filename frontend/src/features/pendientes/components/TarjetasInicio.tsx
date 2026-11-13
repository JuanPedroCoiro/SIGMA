import { Link } from 'react-router-dom'
import { useResumenInicio } from '../hooks/useResumenInicio'
import type { ResumenInicio } from '../types'
import './TarjetasInicio.css'

type Tarjeta = {
  clave: keyof ResumenInicio
  singular: string
  plural: string
  /** Adónde lleva; sin enlace si todavía no hay pantalla (por ejemplo, «Mis OT»). */
  enlace?: string
  /** En rojo si es más que cero. */
  alerta?: boolean
}

// En el orden en que se muestran. Cada rol ve las que el backend no manda en null.
const TARJETAS: Tarjeta[] = [
  {
    clave: 'vencidas',
    singular: 'OT vencida',
    plural: 'OTs vencidas',
    enlace: '/pendientes',
    alerta: true,
  },
  {
    clave: 'porVencer',
    singular: 'OT vence en las próximas 24 h',
    plural: 'OTs vencen en las próximas 24 h',
    enlace: '/pendientes',
  },
  {
    clave: 'pendientesDeAsignar',
    singular: 'OT pendiente de asignar',
    plural: 'OTs pendientes de asignar',
    enlace: '/pendientes?estado=PENDIENTE',
  },
  {
    clave: 'enPausa',
    singular: 'OT en pausa',
    plural: 'OTs en pausa',
    enlace: '/pendientes?estado=EN_PAUSA',
  },
  {
    clave: 'incidentesEnEspera',
    singular: 'incidente en espera de tercero',
    plural: 'incidentes en espera de tercero',
    enlace: '/pendientes',
  },
  {
    clave: 'incidentesHoy',
    singular: 'incidente registrado hoy',
    plural: 'incidentes registrados hoy',
  },
  {
    clave: 'otsDeHoy',
    singular: 'OT de tu cuadrilla para hoy',
    plural: 'OTs de tu cuadrilla para hoy',
  },
]

/** Resumen del inicio por rol (#72): número, texto y enlace a dónde resolverlo. */
export function TarjetasInicio() {
  const resumen = useResumenInicio()

  if (resumen.isPending) return <p>Cargando el resumen...</p>
  if (resumen.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        No se pudo cargar el resumen. Volvé a intentar en unos minutos.
      </p>
    )
  }

  const datos = resumen.data
  const visibles = TARJETAS.filter((tarjeta) => datos[tarjeta.clave] !== null)
  if (visibles.length === 0) return <p>Elegí una opción del menú para empezar.</p>

  return (
    <ul className="tarjetas-inicio" aria-label="Resumen">
      {visibles.map((tarjeta) => {
        const cantidad = datos[tarjeta.clave] ?? 0
        const contenido = (
          <>
            <span className="tarjeta-numero">{cantidad}</span>{' '}
            <span>{cantidad === 1 ? tarjeta.singular : tarjeta.plural}</span>
          </>
        )
        return (
          <li
            key={tarjeta.clave}
            className={tarjeta.alerta && cantidad > 0 ? 'tarjeta tarjeta-alerta' : 'tarjeta'}
          >
            {tarjeta.enlace ? <Link to={tarjeta.enlace}>{contenido}</Link> : <div>{contenido}</div>}
          </li>
        )
      })}
    </ul>
  )
}
