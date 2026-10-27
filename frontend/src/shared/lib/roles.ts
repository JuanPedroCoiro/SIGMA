export type Rol = 'JEFATURA' | 'TECNICO' | 'OPERADOR' | 'ENCARGADO_STOCK'

export const ROLES: Rol[] = ['JEFATURA', 'TECNICO', 'OPERADOR', 'ENCARGADO_STOCK']

export const ETIQUETA_ROL: Record<Rol, string> = {
  JEFATURA: 'Jefatura',
  TECNICO: 'Técnico',
  OPERADOR: 'Operador',
  ENCARGADO_STOCK: 'Encargado de stock',
}
