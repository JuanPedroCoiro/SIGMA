import { http, HttpResponse } from 'msw/http'
import type { CategoriaFalla } from '../../features/ot/types'
import { base } from '../datos'
import { rechazoJefaturaUOperador } from './pendientes'

// Catálogo de categorías de falla (#40): Jefatura y Operador, solo las activas, por nombre.

export const handlersCategorias = [
  http.get('/api/v1/categorias-falla', () => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const activas: CategoriaFalla[] = base.categorias
      .filter((c) => c.activa)
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
      .map(({ id, nombre, prioridadSugerida }) => ({ id, nombre, prioridadSugerida }))
    return HttpResponse.json(activas)
  }),
]
