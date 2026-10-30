export const ROLES = ['JEFATURA', 'TECNICO', 'OPERADOR', 'ENCARGADO_STOCK'] as const

export type Rol = (typeof ROLES)[number]

export const ETIQUETA_ROL: Record<Rol, string> = {
  JEFATURA: 'Jefatura',
  TECNICO: 'Técnico',
  OPERADOR: 'Operador',
  ENCARGADO_STOCK: 'Encargado de stock',
}
