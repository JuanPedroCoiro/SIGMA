import { http, HttpResponse } from 'msw/http'
import type { MiCuadrilla } from '../../features/pendientes/types'
import { base, guardarBase, type UsuarioSimulado } from '../datos'
import { problema, reglaNegocio, sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// La cuadrilla del técnico (HU-20), como MiCuadrillaService: solo el Técnico y sobre su propia
// cuenta. Las OTs no se tocan; «Mis OTs» las filtra por la cuadrilla actual del usuario.

/** Las mismas que muestra «Mis OTs» (OrdenTrabajoConsultaService.contarAbiertasDeCuadrilla). */
const ABIERTAS = ['ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']
const EN_CURSO = ['EN_PROCESO', 'EN_PAUSA']

function respuesta(usuario: UsuarioSimulado): MiCuadrilla {
  const actual = base.cuadrillas.find((c) => c.id === usuario.idCuadrilla) ?? null
  const contar = (estados: string[]) =>
    actual === null
      ? 0
      : base.ordenes.filter((o) => o.idCuadrilla === actual.id && estados.includes(o.estado)).length
  return {
    actual: actual && { id: actual.id, nombre: actual.nombre },
    otsAbiertas: contar(ABIERTAS),
    otsEnCurso: contar(EN_CURSO),
    activas: base.cuadrillas
      .filter((c) => c.activa)
      .sort((a, b) => a.nombre.localeCompare(b.nombre))
      .map(({ id, nombre, zona }) => ({ id, nombre, zona })),
  }
}

/** El técnico en sesión, o la respuesta 401/403. */
function tecnicoEnSesion() {
  const usuario = usuarioEnSesion()
  if (!usuario) return { error: sinSesion() }
  if (usuario.rol !== 'TECNICO') return { error: sinPermiso() }
  return { usuario }
}

export const handlersMiCuadrilla = [
  http.get('/api/v1/mi-cuadrilla', () => {
    const { usuario, error } = tecnicoEnSesion()
    if (error) return error
    return HttpResponse.json(respuesta(usuario))
  }),

  http.post('/api/v1/mi-cuadrilla/cambiar', async ({ request }) => {
    const { usuario, error } = tecnicoEnSesion()
    if (error) return error
    const { idCuadrilla } = (await request.json()) as { idCuadrilla?: number | null }
    if (idCuadrilla == null) {
      return problema(400, 'regla-negocio', 'Datos inválidos', 'Elegí una cuadrilla.')
    }
    const nueva = base.cuadrillas.find((c) => c.id === idCuadrilla)
    if (!nueva) {
      return problema(
        404,
        'no-encontrado',
        'Recurso no encontrado',
        `No existe la cuadrilla ${idCuadrilla}.`,
      )
    }
    // A la misma no cambia nada (ni va a la bitácora).
    if (nueva.id !== usuario.idCuadrilla) {
      if (!nueva.activa) return reglaNegocio(`La cuadrilla ${nueva.nombre} está inactiva.`)
      usuario.idCuadrilla = nueva.id
      guardarBase()
    }
    return HttpResponse.json(respuesta(usuario))
  }),
]
