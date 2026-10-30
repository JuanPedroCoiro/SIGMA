import { createContext } from 'react'
import type { UsuarioSesion } from '../../features/autenticacion/types'
import type { Rol } from '../../shared/lib/roles'

export type { Rol }

export type Usuario = UsuarioSesion

/** `expirada`: se perdió la sesión por un 401 en medio del uso (CU-12 *a), no por un logout. */
export type Sesion =
  | { estado: 'cargando' }
  | { estado: 'anonimo'; expirada?: boolean }
  | { estado: 'autenticado'; usuario: Usuario }

export const SesionContext = createContext<Sesion>({ estado: 'cargando' })
