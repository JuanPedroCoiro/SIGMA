import type { Rol } from '../sesion/sesion'

export type OpcionMenu = {
  ruta: string
  etiqueta: string
  /** Si se omite, la opción la ven todos los roles. */
  roles?: Rol[]
}

export const opcionesMenu: OpcionMenu[] = [{ ruta: '/', etiqueta: 'Estado del sistema' }]

export function opcionesParaRol(rol: Rol): OpcionMenu[] {
  return opcionesMenu.filter((opcion) => !opcion.roles || opcion.roles.includes(rol))
}
