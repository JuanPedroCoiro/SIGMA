import type { ReactNode } from 'react'
import { SesionContext, type Sesion } from './sesion'

// TODO(#20): reemplazar por la sesión real, consultando /me con React Query.
// Mientras tanto, en desarrollo se entra como JEFATURA y en el build se queda anónimo.
const sesionProvisoria: Sesion = import.meta.env.DEV
  ? { estado: 'autenticado', usuario: { nombre: 'Usuario de desarrollo', rol: 'JEFATURA' } }
  : { estado: 'anonimo' }

export function SesionProvider({ children }: { children: ReactNode }) {
  return <SesionContext.Provider value={sesionProvisoria}>{children}</SesionContext.Provider>
}
