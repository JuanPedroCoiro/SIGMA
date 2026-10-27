import { http, HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA, type ErrorCampo } from '../../shared/api/problemDetail'
import { ROLES, type Rol } from '../../shared/lib/roles'
import { base, guardarBase, type UsuarioSimulado } from '../datos'
import { problema, sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Endpoints de usuarios y cuadrillas acordados con el backend (CU-13), solo para JEFATURA.

const TAMANIO_POR_DEFECTO = 20
const TAMANIO_MAXIMO = 100

type DatosUsuario = {
  nombre?: string
  correo?: string
  rol?: Rol
  idCuadrilla?: number | null
  password?: string
}

/** Respuesta 401/403 si quien pide no es JEFATURA; null si puede seguir. */
function exigirJefatura() {
  const usuario = usuarioEnSesion()
  if (!usuario) return sinSesion()
  if (usuario.rol !== 'JEFATURA') return sinPermiso()
  return null
}

function respuesta({ id, nombre, correo, rol, idCuadrilla, activo }: UsuarioSimulado) {
  return { id, nombre, correo, rol, idCuadrilla, activo }
}

function buscar(id: string | readonly string[] | undefined) {
  return base.usuarios.find((u) => u.id === Number(id))
}

const noEncontrado = () =>
  problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe el usuario.')

/** Las mismas validaciones que el Request del backend: 400 con los errores por campo. */
function validar(datos: DatosUsuario, conPassword: boolean): ErrorCampo[] {
  const errores: ErrorCampo[] = []
  if (!datos.nombre?.trim()) errores.push({ campo: 'nombre', mensaje: 'El nombre es obligatorio.' })
  if (!datos.correo?.includes('@')) {
    errores.push({ campo: 'correo', mensaje: 'El correo no es válido.' })
  }
  if (!datos.rol || !ROLES.includes(datos.rol)) {
    errores.push({ campo: 'rol', mensaje: 'El rol no es válido.' })
  }
  if (datos.rol === 'TECNICO' && !base.cuadrillas.some((c) => c.id === datos.idCuadrilla)) {
    errores.push({ campo: 'idCuadrilla', mensaje: 'Un técnico debe pertenecer a una cuadrilla.' })
  }
  if (conPassword && (datos.password ?? '').length < 10) {
    errores.push({ campo: 'password', mensaje: 'Mínimo 10 caracteres.' })
  }
  return errores
}

const datosInvalidos = (errores: ErrorCampo[]) =>
  problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos marcados.', {
    errores,
  })

function correoDuplicado(correo: string, idPropio?: number) {
  return base.usuarios.some((u) => u.correo === correo && u.id !== idPropio)
}

const errorCorreoDuplicado = () =>
  problema(
    409,
    TIPOS_PROBLEMA.correoDuplicado,
    'Correo duplicado',
    'Ya existe un usuario con ese correo.',
  )

export const handlersUsuarios = [
  http.get('/api/v1/usuarios', ({ request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const url = new URL(request.url)
    const pagina = Math.max(0, Number(url.searchParams.get('page') ?? 0))
    const tamanio = Math.min(
      TAMANIO_MAXIMO,
      Number(url.searchParams.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO,
    )
    const ordenados = [...base.usuarios].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
    return HttpResponse.json({
      contenido: ordenados.slice(pagina * tamanio, (pagina + 1) * tamanio).map(respuesta),
      pagina,
      tamanio,
      totalElementos: ordenados.length,
      totalPaginas: Math.ceil(ordenados.length / tamanio),
    })
  }),

  http.get('/api/v1/usuarios/:id', ({ params }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const usuario = buscar(params.id)
    return usuario ? HttpResponse.json(respuesta(usuario)) : noEncontrado()
  }),

  http.post('/api/v1/usuarios', async ({ request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const datos = (await request.json()) as DatosUsuario
    const errores = validar(datos, true)
    if (errores.length > 0) return datosInvalidos(errores)
    const correo = datos.correo!.trim().toLowerCase()
    if (correoDuplicado(correo)) return errorCorreoDuplicado()

    const nuevo: UsuarioSimulado = {
      id: Math.max(...base.usuarios.map((u) => u.id)) + 1,
      nombre: datos.nombre!.trim(),
      correo,
      rol: datos.rol!,
      idCuadrilla: datos.rol === 'TECNICO' ? (datos.idCuadrilla ?? null) : null,
      activo: true,
      password: datos.password!,
      bloqueado: false,
    }
    base.usuarios.push(nuevo)
    guardarBase()
    return HttpResponse.json(respuesta(nuevo), { status: 201 })
  }),

  http.put('/api/v1/usuarios/:id', async ({ params, request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const usuario = buscar(params.id)
    if (!usuario) return noEncontrado()
    const datos = (await request.json()) as DatosUsuario
    const errores = validar(datos, false)
    if (errores.length > 0) return datosInvalidos(errores)
    const correo = datos.correo!.trim().toLowerCase()
    if (correoDuplicado(correo, usuario.id)) return errorCorreoDuplicado()

    usuario.nombre = datos.nombre!.trim()
    usuario.correo = correo
    usuario.rol = datos.rol!
    usuario.idCuadrilla = datos.rol === 'TECNICO' ? (datos.idCuadrilla ?? null) : null
    guardarBase()
    return HttpResponse.json(respuesta(usuario))
  }),

  http.post('/api/v1/usuarios/:id/restablecer-password', async ({ params, request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const usuario = buscar(params.id)
    if (!usuario) return noEncontrado()
    const { password = '' } = (await request.json()) as { password?: string }
    if (password.length < 10) {
      return datosInvalidos([{ campo: 'password', mensaje: 'Mínimo 10 caracteres.' }])
    }
    usuario.password = password
    usuario.bloqueado = false // restablecer también desbloquea (CU-13b)
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/usuarios/:id/desactivar', ({ params }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const usuario = buscar(params.id)
    if (!usuario) return noEncontrado()
    if (usuario.id === base.idSesion) {
      return problema(
        422,
        'regla-negocio',
        'Regla de negocio no cumplida',
        'No podés desactivar tu propia cuenta.',
      )
    }
    usuario.activo = false
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/api/v1/usuarios/:id/activar', ({ params }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const usuario = buscar(params.id)
    if (!usuario) return noEncontrado()
    usuario.activo = true
    guardarBase()
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/v1/cuadrillas', () => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    return HttpResponse.json(base.cuadrillas)
  }),
]
