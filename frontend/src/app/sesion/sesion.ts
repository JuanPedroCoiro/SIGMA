import { createContext } from 'react'

export type Rol = 'JEFATURA' | 'TECNICO' | 'OPERADOR' | 'ENCARGADO_STOCK'

export type Usuario = {
  nombre: string
  rol: Rol
}

export type Sesion =
  { estado: 'cargando' } | { estado: 'anonimo' } | { estado: 'autenticado'; usuario: Usuario }

export const SesionContext = createContext<Sesion>({ estado: 'cargando' })
