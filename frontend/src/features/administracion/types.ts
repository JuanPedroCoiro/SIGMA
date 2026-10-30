import type { Rol } from '../../shared/lib/roles'

// Reflejan los DTOs acordados con el backend (#19); se escriben a mano hasta tener el OpenAPI.

export type Usuario = {
  id: number
  nombre: string
  /** Es el usuario de acceso (CU-13a). */
  correo: string
  rol: Rol
  idCuadrilla: number | null
  activo: boolean
}

/** PUT /api/v1/usuarios/{id}: sin contraseña (CU-13b). */
export type DatosUsuario = {
  nombre: string
  correo: string
  rol: Rol
  idCuadrilla: number | null
}

/** POST /api/v1/usuarios: la Jefatura escribe la contraseña inicial (CU-13a). */
export type NuevoUsuario = DatosUsuario & { password: string }

export type Cuadrilla = {
  id: number
  nombre: string
  zona: string | null
  activa: boolean
}
