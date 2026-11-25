import { http, HttpResponse } from 'msw/http'
import {
  puedeAnularse,
  puedeAsignarse,
  type IncidenteDeOT,
  type MotivoPausa,
  type OrdenTrabajoDetalle,
  type PausaOT,
} from '../../features/ot/types'
import { TIPOS_PROBLEMA, type ErrorCampo } from '../../shared/api/problemDetail'
import { MAXIMO_FOTOS_CIERRE } from '../../features/ot/fotosCierre'
import { MOMENTOS_FOTO, type DatosCierre, type MomentoFoto } from '../../features/ot/types'
import { hoyEnMontevideo } from '../../shared/lib/fechas'
import { base, guardarBase, type OrdenSimulada, type PausaSimulada } from '../datos'
import {
  exigirJefatura,
  problema,
  reglaNegocio,
  sinPermiso,
  sinSesion,
  usuarioEnSesion,
} from '../problemas'
import { archivosDe, camposDeTexto, detectarTipo, partesDelMultipart } from './fotos'

// Detalle (con instalación, cuadrilla e incidente, #57), pausar y reanudar una OT (#39). Técnico (solo su cuadrilla), Operador y Jefatura.
// Asignar y carga por cuadrilla (#41), solo la Jefatura.
// Iniciar y cierre técnico con fotos (HU-07, HU-08), solo el técnico de la cuadrilla de la OT.

const MINUTO = 60_000
const PAUSABLES = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO']
const ROLES = ['JEFATURA', 'OPERADOR', 'TECNICO']

/** Las que una cuadrilla tiene entre manos, como OrdenTrabajoConsultaService. */
const EN_MANOS_DE_LA_CUADRILLA = ['ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']

type DatosPausa = { motivo?: MotivoPausa; detalle?: string | null }

type DatosAsignacion = { idsOrdenTrabajo?: number[]; idCuadrilla?: number; fechaEjecucion?: string }

function minutos(desde: string, hasta: number) {
  return Math.floor((hasta - new Date(desde).getTime()) / MINUTO)
}

function nombreUsuario(id: number) {
  return base.usuarios.find((u) => u.id === id)?.nombre ?? ''
}

/** La pausa con los nombres de quién pausó y quién reanudó. */
function conNombres(pausa: PausaSimulada): PausaOT {
  return {
    ...pausa,
    nombreUsuarioPausa: nombreUsuario(pausa.idUsuarioPausa),
    nombreUsuarioReanudacion:
      pausa.idUsuarioReanudacion === null ? null : nombreUsuario(pausa.idUsuarioReanudacion),
  }
}

/** El incidente que originó la OT, con el nombre de la categoría (DetalleOt.Incidente). */
function incidenteDe(orden: OrdenSimulada): IncidenteDeOT | null {
  const incidente = base.incidentes.find((i) => i.id === orden.idIncidente)
  if (!incidente) return null
  return {
    id: incidente.id,
    numero: incidente.numero,
    origen: incidente.origen,
    categoria: base.categorias.find((c) => c.id === incidente.idCategoriaFalla)?.nombre ?? '',
    prioridad: incidente.prioridad,
    descripcion: incidente.descripcion,
    tercero: incidente.tercero,
    causa: incidente.causa,
  }
}

const CERRADAS = ['CERRADA_TECNICA', 'VALIDADA', 'ANULADA']

/**
 * Como OrdenTrabajo.estaVencida: tiene fecha límite, sigue abierta y ya la pasó. En pausa por un
 * tercero no vence. La usan el detalle y la ficha de la instalación (HU-10).
 */
export function estaVencida(orden: OrdenSimulada, ahora: number = Date.now()) {
  const abierta = orden.pausas.find((p) => p.fin === null)
  return (
    !CERRADAS.includes(orden.estado) &&
    abierta?.motivo !== 'TERCERO' &&
    orden.fechaLimite !== null &&
    new Date(orden.fechaLimite).getTime() < ahora
  )
}

/** La OT como la devuelve OrdenTrabajoDetalle, con lo que se calcula al consultarla (#57). */
function respuesta(orden: OrdenSimulada): OrdenTrabajoDetalle {
  const ahora = Date.now()
  const abierta = orden.pausas.find((p) => p.fin === null) ?? null
  const cerrada = CERRADAS.includes(orden.estado)
  const enPausaMinutos: Partial<Record<MotivoPausa, number>> = {}
  for (const pausa of orden.pausas) {
    const duracion = minutos(pausa.inicio, pausa.fin ? new Date(pausa.fin).getTime() : ahora)
    enPausaMinutos[pausa.motivo] = (enPausaMinutos[pausa.motivo] ?? 0) + duracion
  }
  const totalMinutos = minutos(orden.creadaEn, ahora)
  const pausado = Object.values(enPausaMinutos).reduce((a, b) => a + b, 0)
  const instalacion = base.instalaciones.find((i) => i.id === orden.idInstalacion)
  const cuadrilla = base.cuadrillas.find((c) => c.id === orden.idCuadrilla)
  return {
    id: orden.id,
    numero: orden.numero,
    tipo: orden.idIncidente === null ? 'PREVENTIVA' : 'CORRECTIVA',
    estado: orden.estado,
    instalacion: {
      id: orden.idInstalacion,
      codigo: instalacion?.codigo ?? '',
      nombre: instalacion?.nombre ?? '',
    },
    cuadrilla: cuadrilla ? { id: cuadrilla.id, nombre: cuadrilla.nombre } : null,
    incidente: incidenteDe(orden),
    fechaEjecucion: orden.fechaEjecucion,
    inicioSla: orden.inicioSla,
    fechaLimite: orden.fechaLimite,
    slaDefinido: orden.fechaLimite !== null,
    vencida: estaVencida(orden, ahora),
    pausaAbierta: abierta && conNombres(abierta),
    tiempos: cerrada
      ? null
      : { totalMinutos, enPausaMinutos, efectivoMinutos: totalMinutos - pausado },
    pausas: orden.pausas.map(conNombres),
    iniciadaEn: orden.iniciadaEn,
    cierre: orden.cierre && {
      diagnostico: orden.cierre.diagnostico,
      solucion: orden.cierre.solucion,
      observaciones: orden.cierre.observaciones,
      cerradaEn: orden.cierre.cerradaEn,
    },
  }
}

/** 401, 403 o 404 como el backend; si puede seguir, la OT. */
function buscarAccesible(id: string | readonly string[] | undefined) {
  const usuario = usuarioEnSesion()
  if (!usuario) return { rechazo: sinSesion() }
  if (!ROLES.includes(usuario.rol)) return { rechazo: sinPermiso() }
  const orden = base.ordenes.find((o) => o.id === Number(id))
  if (!orden) {
    return {
      rechazo: problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe la OT.'),
    }
  }
  if (usuario.rol === 'TECNICO' && orden.idCuadrilla !== usuario.idCuadrilla) {
    return { rechazo: sinPermiso() }
  }
  return { orden, usuario }
}

const transicionInvalida = (operacion: string, orden: OrdenSimulada) =>
  problema(
    400,
    TIPOS_PROBLEMA.transicionInvalida,
    'Regla de negocio no cumplida',
    `No se puede ${operacion} una OT en estado ${orden.estado}.`,
    // Como TransicionInvalidaException (CU-3 8b): la pantalla decide con el estado actual.
    { estadoActual: orden.estado },
  )

/**
 * Iniciar y cerrar (EjecucionOtController): primero el rol (@PreAuthorize, solo TECNICO); el
 * acceso a la OT (AccesoOt) lo resuelve `accesoDelTecnico`, después de validar el pedido.
 */
function rechazoNoTecnico() {
  const usuario = usuarioEnSesion()
  if (!usuario) return sinSesion()
  if (usuario.rol !== 'TECNICO') return sinPermiso()
  return null
}

/** Como AccesoOt.buscarParaModificar para el técnico: 404 si no existe, 403 si no es suya. */
function accesoDelTecnico(id: string | readonly string[] | undefined) {
  const usuario = usuarioEnSesion()
  const orden = base.ordenes.find((o) => o.id === Number(id))
  if (!orden) {
    return {
      rechazo: problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe la OT.'),
    }
  }
  if (!usuario || orden.idCuadrilla !== usuario.idCuadrilla) return { rechazo: sinPermiso() }
  return { orden }
}

const fotoInvalida = (detalle: string) =>
  problema(400, TIPOS_PROBLEMA.fotoInvalida, 'Regla de negocio no cumplida', detalle)

const MAXIMO_TEXTO_CIERRE = 2000

/** Los errores de @Valid sobre CerrarOtRequest, con los mismos mensajes. */
function erroresDelCierre(datos: Partial<DatosCierre>): ErrorCampo[] {
  const errores: ErrorCampo[] = []
  if (!datos.idOperacion) {
    errores.push({ campo: 'idOperacion', mensaje: 'Falta el identificador de la operación.' })
  }
  const largo = (texto: string | undefined) => (texto ?? '').length > MAXIMO_TEXTO_CIERRE
  if (!datos.diagnostico?.trim()) {
    errores.push({ campo: 'diagnostico', mensaje: 'Indicá el diagnóstico.' })
  } else if (largo(datos.diagnostico)) {
    errores.push({ campo: 'diagnostico', mensaje: 'El diagnóstico admite hasta 2000 caracteres.' })
  }
  if (!datos.solucion?.trim()) {
    errores.push({ campo: 'solucion', mensaje: 'Indicá la solución.' })
  } else if (largo(datos.solucion)) {
    errores.push({ campo: 'solucion', mensaje: 'La solución admite hasta 2000 caracteres.' })
  }
  if (largo(datos.observaciones)) {
    errores.push({
      campo: 'observaciones',
      mensaje: 'Las observaciones admiten hasta 2000 caracteres.',
    })
  }
  return errores
}

export const handlersOrdenes = [
  // Antes que /ordenes/:id: si no, MSW tomaría «carga-por-cuadrilla» como el id de una OT.
  http.get('/api/v1/ordenes/carga-por-cuadrilla', ({ request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const fecha = new URL(request.url).searchParams.get('fecha')
    if (!fecha) {
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Falta la fecha.')
    }
    const carga = new Map<number, number>()
    for (const orden of base.ordenes) {
      if (
        orden.idCuadrilla !== null &&
        orden.fechaEjecucion === fecha &&
        EN_MANOS_DE_LA_CUADRILLA.includes(orden.estado)
      ) {
        carga.set(orden.idCuadrilla, (carga.get(orden.idCuadrilla) ?? 0) + 1)
      }
    }
    return HttpResponse.json([...carga].map(([idCuadrilla, ordenes]) => ({ idCuadrilla, ordenes })))
  }),

  // Como AsignacionOtService: todas o ninguna.
  http.post('/api/v1/ordenes/asignar', async ({ request }) => {
    const rechazo = exigirJefatura()
    if (rechazo) return rechazo
    const { idsOrdenTrabajo, idCuadrilla, fechaEjecucion } =
      (await request.json()) as DatosAsignacion

    const errores: ErrorCampo[] = []
    if (!idsOrdenTrabajo?.length) {
      errores.push({ campo: 'idsOrdenTrabajo', mensaje: 'Elegí al menos una OT.' })
    }
    if (!idCuadrilla) errores.push({ campo: 'idCuadrilla', mensaje: 'Elegí la cuadrilla.' })
    if (!fechaEjecucion) {
      errores.push({ campo: 'fechaEjecucion', mensaje: 'Elegí la fecha de ejecución.' })
    }
    if (!idsOrdenTrabajo?.length || !idCuadrilla || !fechaEjecucion) {
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
        errores,
      })
    }

    const cuadrilla = base.cuadrillas.find((c) => c.id === idCuadrilla && c.activa)
    if (!cuadrilla) {
      return reglaNegocio(`La cuadrilla ${idCuadrilla} no existe o está inactiva.`)
    }
    // Las fechas «AAAA-MM-DD» se comparan bien como texto.
    if (fechaEjecucion < hoyEnMontevideo()) {
      return problema(
        400,
        TIPOS_PROBLEMA.fechaAnterior,
        'Regla de negocio no cumplida',
        'La fecha de ejecución no puede ser anterior a hoy.',
      )
    }

    const ids = [...new Set(idsOrdenTrabajo)]
    const faltantes = ids.filter((id) => !base.ordenes.some((o) => o.id === id))
    if (faltantes.length > 0) {
      return problema(
        404,
        'no-encontrado',
        'Recurso no encontrado',
        `No existen las órdenes de trabajo [${faltantes.join(', ')}].`,
      )
    }
    const ordenes = base.ordenes.filter((o) => ids.includes(o.id))
    // Primero se revisan todas; recién después se asigna (todas o ninguna).
    const noAsignable = ordenes.find((o) => !puedeAsignarse(o.estado))
    if (noAsignable) {
      return problema(
        400,
        TIPOS_PROBLEMA.transicionInvalida,
        'Regla de negocio no cumplida',
        `${noAsignable.numero}: No se puede asignar una OT en estado ${noAsignable.estado}.`,
      )
    }
    for (const orden of ordenes) {
      orden.estado = 'ASIGNADA'
      orden.idCuadrilla = cuadrilla.id
      orden.fechaEjecucion = fechaEjecucion
    }
    guardarBase()
    return HttpResponse.json(ordenes.map(respuesta))
  }),

  http.get('/api/v1/ordenes/:id', ({ params }) => {
    const { rechazo, orden } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    return HttpResponse.json(respuesta(orden))
  }),

  // Fotos del cierre técnico (#90), como FotosOtController: quien puede ver la OT.
  http.get('/api/v1/ordenes/:id/fotos', ({ params }) => {
    const { rechazo, orden } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    return HttpResponse.json(
      base.fotosCierre
        .filter((foto) => foto.idOrdenTrabajo === orden.id)
        .map(({ idOrdenTrabajo, ...foto }) => ({
          ...foto,
          url: `/api/v1/ordenes/${idOrdenTrabajo}/fotos/${foto.id}`,
        })),
    )
  }),

  http.get('/api/v1/ordenes/:id/fotos/:idFoto', ({ params }) => {
    const { rechazo, orden } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    const existe = base.fotosCierre.some(
      (foto) => foto.id === Number(params.idFoto) && foto.idOrdenTrabajo === orden.id,
    )
    if (!existe) {
      return problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe la foto.')
    }
    return new HttpResponse(
      '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160"><rect width="160" height="160" fill="#e5e7eb"/></svg>',
      { headers: { 'Content-Type': 'image/svg+xml' } },
    )
  }),

  http.post('/api/v1/ordenes/:id/pausar', async ({ params, request }) => {
    const { rechazo, orden, usuario } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    const datos = (await request.json()) as DatosPausa
    if (!datos.motivo) {
      const errores: ErrorCampo[] = [{ campo: 'motivo', mensaje: 'Elegí el motivo de la pausa.' }]
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
        errores,
      })
    }
    if (!PAUSABLES.includes(orden.estado)) return transicionInvalida('pausar', orden)
    const detalle = datos.detalle?.trim() || null
    if ((datos.motivo === 'TERCERO' || datos.motivo === 'OTRO') && !detalle) {
      return reglaNegocio(
        datos.motivo === 'TERCERO'
          ? 'Indicá qué tercero causa la pausa.'
          : 'Detallá el motivo de la pausa.',
      )
    }
    orden.pausas.push({
      motivo: datos.motivo,
      detalle,
      inicio: new Date().toISOString(),
      fin: null,
      idUsuarioPausa: usuario.id,
      idUsuarioReanudacion: null,
    })
    orden.estadoAnteriorPausa = orden.estado
    orden.estado = 'EN_PAUSA'
    guardarBase()
    return HttpResponse.json(respuesta(orden))
  }),

  http.post('/api/v1/ordenes/:id/reanudar', ({ params }) => {
    const { rechazo, orden, usuario } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    const pausa = orden.pausas.find((p) => p.fin === null)
    if (orden.estado !== 'EN_PAUSA' || !pausa) return transicionInvalida('reanudar', orden)
    const ahora = new Date()
    pausa.fin = ahora.toISOString()
    pausa.idUsuarioReanudacion = usuario.id
    // Tras un tercero, el SLA arranca completo: se conserva el plazo y se cuenta desde ahora.
    if (pausa.motivo === 'TERCERO' && orden.inicioSla && orden.fechaLimite) {
      const plazo = new Date(orden.fechaLimite).getTime() - new Date(orden.inicioSla).getTime()
      orden.inicioSla = ahora.toISOString()
      orden.fechaLimite = new Date(ahora.getTime() + plazo).toISOString()
    }
    orden.estado = orden.estadoAnteriorPausa ?? 'PENDIENTE'
    orden.estadoAnteriorPausa = null
    guardarBase()
    return HttpResponse.json(respuesta(orden))
  }),

  // CU-3 paso 2: Asignada → En proceso, con la hora de inicio.
  http.post('/api/v1/ordenes/:id/iniciar', ({ params }) => {
    const rechazoRol = rechazoNoTecnico()
    if (rechazoRol) return rechazoRol
    const { rechazo, orden } = accesoDelTecnico(params.id)
    if (rechazo) return rechazo
    if (orden.estado !== 'ASIGNADA') return transicionInvalida('iniciar', orden)
    orden.estado = 'EN_PROCESO'
    orden.iniciadaEn = new Date().toISOString()
    guardarBase()
    return HttpResponse.json(respuesta(orden))
  }),

  // CU-3 pasos 6 a 10 con las fotos (HU-08), en el orden en que valida el backend: primero el
  // controller (datos y momentos), después EjecucionOtService (acceso, idempotencia, estado) y
  // FotosCierreService.validar (cantidad y tipo de las fotos).
  http.post('/api/v1/ordenes/:id/cerrar', async ({ params, request }) => {
    const rechazoRol = rechazoNoTecnico()
    if (rechazoRol) return rechazoRol

    const partes = await partesDelMultipart(request)
    const parteDatos = partes.find((p) => p.campo === 'datos')
    const datos: Partial<DatosCierre> = parteDatos
      ? (JSON.parse(new TextDecoder().decode(parteDatos.bytes)) as Partial<DatosCierre>)
      : {}
    const errores = erroresDelCierre(datos)
    if (errores.length > 0) {
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
        errores,
      })
    }
    const fotos = archivosDe(partes, 'fotos')
    const momentos = camposDeTexto(partes, 'momentos')
    if (
      fotos.length !== momentos.length ||
      !momentos.every((m) => MOMENTOS_FOTO.includes(m as MomentoFoto))
    ) {
      return fotoInvalida('Indicá el momento de cada foto: antes, durante o después del trabajo.')
    }

    const { rechazo, orden } = accesoDelTecnico(params.id)
    if (rechazo) return rechazo
    // Reintento de un cierre ya hecho (R8): el mismo resultado, sin cerrar ni guardar de nuevo.
    if (orden.cierre?.idOperacion === datos.idOperacion) {
      return HttpResponse.json(respuesta(orden))
    }
    if (base.ordenes.some((o) => o.cierre?.idOperacion === datos.idOperacion)) {
      return problema(
        409,
        TIPOS_PROBLEMA.operacionRepetida,
        'Conflicto con el estado actual',
        'Ese identificador de operación ya se usó para cerrar otra OT.',
      )
    }
    if (orden.estado !== 'EN_PROCESO') return transicionInvalida('cerrar', orden)
    if (fotos.length === 0) return fotoInvalida('El cierre técnico exige al menos una foto.')
    if (fotos.length > MAXIMO_FOTOS_CIERRE) {
      return fotoInvalida(`El cierre técnico admite hasta ${MAXIMO_FOTOS_CIERRE} fotos.`)
    }
    const tipos: string[] = []
    for (const foto of fotos) {
      const tipo = detectarTipo(foto.bytes)
      if (!tipo) return fotoInvalida(`«${foto.nombre}» no es una imagen JPG, PNG ni WEBP.`)
      tipos.push(tipo)
    }

    const ahora = new Date().toISOString()
    fotos.forEach((foto, i) => {
      base.fotosCierre.push({
        id: Math.max(0, ...base.fotosCierre.map((f) => f.id)) + 1,
        idOrdenTrabajo: orden.id,
        nombreOriginal: foto.nombre,
        tipo: tipos[i],
        tamanioBytes: foto.bytes.byteLength,
        subidaEn: ahora,
        momento: momentos[i] as MomentoFoto,
      })
    })
    orden.estado = 'CERRADA_TECNICA'
    orden.cierre = {
      idOperacion: datos.idOperacion ?? '',
      diagnostico: datos.diagnostico?.trim() ?? '',
      solucion: datos.solucion?.trim() ?? '',
      observaciones: datos.observaciones?.trim() || null,
      cerradaEn: ahora,
    }
    guardarBase()
    return HttpResponse.json(respuesta(orden))
  }),

  http.post('/api/v1/ordenes/:id/anular', async ({ params, request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'JEFATURA') return sinPermiso()
    const orden = base.ordenes.find((o) => o.id === Number(params.id))
    if (!orden) {
      return problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe la OT.')
    }
    const { motivo } = (await request.json()) as { motivo?: string }
    if (!motivo?.trim()) {
      const errores: ErrorCampo[] = [
        { campo: 'motivo', mensaje: 'Indicá el motivo de la anulación.' },
      ]
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
        errores,
      })
    }
    if (!puedeAnularse(orden.estado, orden.estadoAnteriorPausa)) {
      return transicionInvalida('anular', orden)
    }
    // Como el backend: si estaba en pausa, la pausa se cierra con quien anula.
    const pausa = orden.pausas.find((p) => p.fin === null)
    if (pausa) {
      pausa.fin = new Date().toISOString()
      pausa.idUsuarioReanudacion = usuario.id
    }
    orden.estado = 'ANULADA'
    orden.estadoAnteriorPausa = null
    guardarBase()
    return HttpResponse.json(respuesta(orden))
  }),
]
