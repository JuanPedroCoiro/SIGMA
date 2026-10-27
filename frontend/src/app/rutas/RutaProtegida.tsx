import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Rol } from '../sesion/sesion'
import { useSesion } from '../sesion/useSesion'

type Props = {
  /** Si se indica, solo entran estos roles; el resto vuelve al inicio. */
  roles?: Rol[]
}

export function RutaProtegida({ roles }: Props) {
  const sesion = useSesion()
  const location = useLocation()

  if (sesion.estado === 'cargando') {
    return <p style={{ padding: '2rem' }}>Cargando...</p>
  }

  if (sesion.estado === 'anonimo') {
    return (
      <Navigate
        to="/iniciar-sesion"
        replace
        state={{ desde: location, expirada: sesion.expirada }}
      />
    )
  }

  if (roles && !roles.includes(sesion.usuario.rol)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
