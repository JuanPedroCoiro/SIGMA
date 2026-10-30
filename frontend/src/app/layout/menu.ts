import type { Rol } from '../sesion/sesion'

export type OpcionMenu = {
  ruta: string
  etiqueta: string
  /** Si se omite, la opción la ven todos los roles. */
  roles?: Rol[]
}

// Cada rol ve solo sus funciones (CU-12). Las rutas repiten la restricción en router.tsx.
export const opcionesMenu: OpcionMenu[] = [
  { ruta: '/', etiqueta: 'Inicio' },
  { ruta: '/usuarios', etiqueta: 'Usuarios', roles: ['JEFATURA'] },
  { ruta: '/estado', etiqueta: 'Estado del sistema', roles: ['JEFATURA'] },
]

export function opcionesParaRol(rol: Rol): OpcionMenu[] {
  return opcionesMenu.filter((opcion) => !opcion.roles || opcion.roles.includes(rol))
}
