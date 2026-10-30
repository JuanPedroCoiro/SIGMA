import { isAxiosError } from 'axios'
import { cliente } from '../../../shared/api/cliente'
import type { Credenciales, UsuarioSesion } from '../types'

export const CLAVE_SESION = ['sesion'] as const

/** Usuario en sesión, o null si no hay sesión (el backend responde 401). */
export async function obtenerSesion(): Promise<UsuarioSesion | null> {
  try {
    const res = await cliente.get<UsuarioSesion>('/sesion')
    return res.data
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 401) return null
    throw error
  }
}

/** El login de Spring Security es un formulario (application/x-www-form-urlencoded). */
export async function iniciarSesion({ correo, contrasena }: Credenciales): Promise<void> {
  await cliente.post('/sesion', new URLSearchParams({ correo, contrasena }))
}

export async function cerrarSesion(): Promise<void> {
  await cliente.delete('/sesion')
}
