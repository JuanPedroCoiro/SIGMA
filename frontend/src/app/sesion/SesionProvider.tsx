import { useEffect, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CLAVE_SESION, obtenerSesion } from '../../features/autenticacion/api/sesionApi'
import { registrarManejadorSesionExpirada } from '../../shared/api/cliente'
import { SesionContext, type Sesion } from './sesion'

/**
 * Sesión real del backend (GET /api/v1/sesion). Se vuelve a consultar cuando la pestaña recupera
 * el foco, así que una sesión vencida se detecta aunque no se haga ningún pedido.
 */
export function SesionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const consulta = useQuery({ queryKey: CLAVE_SESION, queryFn: obtenerSesion, retry: false })
  const [expirada, setExpirada] = useState(false)

  // Al volver a ingresar se olvida el aviso de sesión vencida.
  if (consulta.data && expirada) setExpirada(false)

  useEffect(() => {
    registrarManejadorSesionExpirada(() => {
      setExpirada(true)
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== CLAVE_SESION[0] })
      queryClient.setQueryData(CLAVE_SESION, null)
    })
    return () => registrarManejadorSesionExpirada(null)
  }, [queryClient])

  let sesion: Sesion
  if (consulta.isPending) {
    sesion = { estado: 'cargando' }
  } else if (consulta.data) {
    sesion = { estado: 'autenticado', usuario: consulta.data }
  } else {
    // Sin sesión, o el backend no respondió: en los dos casos se pide iniciar sesión.
    sesion = { estado: 'anonimo', expirada }
  }

  return <SesionContext.Provider value={sesion}>{children}</SesionContext.Provider>
}
