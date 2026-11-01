import type { Rol } from '../../shared/lib/roles'

/** Respuesta de GET /api/v1/sesion («quién soy»). */
export type UsuarioSesion = {
  id: number
  nombre: string
  correo: string
  rol: Rol
  idCuadrilla: number | null
}

export type Credenciales = {
  correo: string
  contrasena: string
}
