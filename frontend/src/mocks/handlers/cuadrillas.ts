import { http, HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA, type ErrorCampo } from '../../shared/api/problemDetail'
import { base, guardarBase, type CuadrillaSimulada } from '../datos'
import { exigirJefatura, problema, reglaNegocio } from '../problemas'

// Endpoints de cuadrillas acordados con el backend (#49), solo para JEFATURA.

type DatosCuadrilla = { nombre?: string; zona?: string | null }

function tecnicosActivos(idCuadrilla: number) {
  return base.usuarios.filter(
    (u) => u.rol === 'TECNICO' && u.activo && u.idCuadrilla === idCuadrilla,
  ).length
}

function respuesta(cuadrilla: CuadrillaSimulada) {
  return { ...cuadrilla, tecnicosActivos: tecnicosActivos(cuadrilla.id) }
}

function buscar(id: string | readonly string[] | undefined) {
  return base.cuadrillas.find((c) => c.id === Number(id))
}

const noEncontrada = () =>
  problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe la cuadrilla.')

/** Las mismas validaciones que CuadrillaRequest: 400 con los errores por campo. */
function validar(datos: DatosCuadrilla): ErrorCampo[] {
  const errores: ErrorCampo[] = []
  const nombre = datos.nombre?.trim() ?? ''
  if (!nombre) errores.push({ campo: 'nombre', mensaje: 'Ingresá el nombre.' })
  if (nombre.length > 100) {
    errores.push({ campo: 'nombre', mensaje: 'El nombre admite hasta 100 caracteres.' })
  }
  if ((datos.zona ?? '').length > 100) {
    errores.push({ campo: 'zona', mensaje: 'La zona admite hasta 100 caracteres.' })
  }
  return errores
}

/** 400 si los datos son inválidos, 409 si el nombre ya existe (sin distinguir mayúsculas). */
function rechazar(datos: DatosCuadrilla, idPropio?: number) {
  const errores = validar(datos)
  if (errores.length > 0) {
    return problema(
      400,
      TIPOS_PROBLEMA.validacion,
      'Datos inválidos',
      'Revisá los campos marcados.',
      {
        errores,
      },
    )
  }
  const nombre = datos.nombre!.trim()
  const repetido = base.cuadrillas.some(
    (c) => c.nombre.toLowerCase() === nombre.toLowerCase() && c.id !== idPropio,
  )
  if (repetido) {
    return problema(
      409,
      TIPOS_PROBLEMA.cuadrillaDuplicada,
      'Conflicto con el estado actual',
      `Ya existe una cuadrilla con el nombre ${nombre}.`,
    )
  }
  return null
}

const zonaNormalizada = (zona?: string | null) => zona?.trim() || null

export const handlersCuadrillas = [
  http.get('/api/v1/cuadrillas', () => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const ordenadas = [...base.cuadrillas].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
    return HttpResponse.json(ordenadas.map(respuesta))
  }),

  http.post('/api/v1/cuadrillas', async ({ request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const datos = (await request.json()) as DatosCuadrilla
    const invalida = rechazar(datos)
    if (invalida) return invalida

    const nueva: CuadrillaSimulada = {
      id: Math.max(0, ...base.cuadrillas.map((c) => c.id)) + 1,
      nombre: datos.nombre!.trim(),
      zona: zonaNormalizada(datos.zona),
      activa: true,
    }
    base.cuadrillas.push(nueva)
    guardarBase()
    return HttpResponse.json(respuesta(nueva), { status: 201 })
  }),

  http.put('/api/v1/cuadrillas/:id', async ({ params, request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const cuadrilla = buscar(params.id)
    if (!cuadrilla) return noEncontrada()
    const datos = (await request.json()) as DatosCuadrilla
    const invalida = rechazar(datos, cuadrilla.id)
    if (invalida) return invalida

    cuadrilla.nombre = datos.nombre!.trim()
    cuadrilla.zona = zonaNormalizada(datos.zona)
    guardarBase()
    return HttpResponse.json(respuesta(cuadrilla))
  }),

  http.post('/api/v1/cuadrillas/:id/desactivar', ({ params }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const cuadrilla = buscar(params.id)
    if (!cuadrilla) return noEncontrada()
    const cantidad = tecnicosActivos(cuadrilla.id)
    if (cantidad > 0) {
      const tecnicos = cantidad === 1 ? 'técnico activo' : 'técnicos activos'
      return reglaNegocio(
        `La cuadrilla ${cuadrilla.nombre} tiene ${cantidad} ${tecnicos}. ` +
          'Primero cambialos de cuadrilla.',
      )
    }
    cuadrilla.activa = false
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/cuadrillas/:id/activar', ({ params }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const cuadrilla = buscar(params.id)
    if (!cuadrilla) return noEncontrada()
    cuadrilla.activa = true
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),
]
