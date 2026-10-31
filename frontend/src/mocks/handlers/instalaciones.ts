import { http, HttpResponse } from 'msw/http'
import { base } from '../datos'
import { sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Endpoints de instalaciones acordados con el backend (HU-02): el listado es para JEFATURA y
// OPERADOR.

const TAMANIO_POR_DEFECTO = 20
const TAMANIO_MAXIMO = 100

export const handlersInstalaciones = [
  http.get('/api/v1/instalaciones', ({ request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'JEFATURA' && usuario.rol !== 'OPERADOR') return sinPermiso()
    const url = new URL(request.url)
    const pagina = Math.max(0, Number(url.searchParams.get('page') ?? 0))
    const tamanio = Math.min(
      TAMANIO_MAXIMO,
      Number(url.searchParams.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO,
    )
    const ordenadas = [...base.instalaciones].sort((a, b) => a.codigo.localeCompare(b.codigo))
    return HttpResponse.json({
      contenido: ordenadas.slice(pagina * tamanio, (pagina + 1) * tamanio),
      pagina,
      tamanio,
      totalElementos: ordenadas.length,
      totalPaginas: Math.ceil(ordenadas.length / tamanio),
    })
  }),
]
