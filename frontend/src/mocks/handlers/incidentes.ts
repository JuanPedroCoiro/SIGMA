import { http, HttpResponse } from 'msw/http'
import {
  esCategoriaOtra,
  MINIMO_DETALLE_OTRA,
  ORIGENES_INCIDENTE,
  type DatosIncidente,
  type IncidenteResponse,
  type Prioridad,
} from '../../features/ot/types'
import { TIPOS_PROBLEMA, type ErrorCampo } from '../../shared/api/problemDetail'
import { base, guardarBase, type IncidenteSimulado, type OrdenSimulada } from '../datos'
import { problema, reglaNegocio } from '../problemas'
import { rechazoJefaturaUOperador } from './pendientes'

// Registrar un incidente (#40) y resolver la espera de un tercero (#38): cerrar sin OT o generar
// la OT. Jefatura y Operador.

const HORA = 3_600_000
/** Plazos de resolución de las reglas de prueba (db/datos-prueba del backend). */
const HORAS_RESOLUCION: Record<Prioridad, number> = { ALTA: 8, MEDIA: 48, BAJA: 120 }
const PRIORIDADES: readonly string[] = ['ALTA', 'MEDIA', 'BAJA']

function respuesta(incidente: IncidenteSimulado, orden: OrdenSimulada | null): IncidenteResponse {
  return {
    id: incidente.id,
    numero: incidente.numero,
    estado: incidente.estado,
    prioridad: incidente.prioridad,
    tercero: incidente.tercero,
    causa: incidente.causa,
    ordenTrabajo: orden && {
      id: orden.id,
      numero: orden.numero,
      inicioSla: orden.inicioSla ?? orden.creadaEn,
      fechaLimite: orden.fechaLimite,
      slaDefinido: orden.fechaLimite !== null,
    },
  }
}

/** La OT correctiva PENDIENTE del incidente; sin regla de SLA, «SLA sin definir» (8a). */
function crearOrden(incidente: IncidenteSimulado): OrdenSimulada {
  const ahora = new Date()
  const id = Math.max(0, ...base.ordenes.map((o) => o.id)) + 1
  const instalacion = base.instalaciones.find((i) => i.id === incidente.idInstalacion)
  const orden: OrdenSimulada = {
    id,
    numero: `OT-${String(id).padStart(4, '0')}`,
    estado: 'PENDIENTE',
    idCuadrilla: null,
    fechaEjecucion: null,
    inicioSla: ahora.toISOString(),
    fechaLimite: instalacion?.sinReglaSla
      ? null
      : new Date(ahora.getTime() + HORAS_RESOLUCION[incidente.prioridad] * HORA).toISOString(),
    creadaEn: ahora.toISOString(),
    estadoAnteriorPausa: null,
    pausas: [],
    prioridad: incidente.prioridad,
    idInstalacion: incidente.idInstalacion,
    idIncidente: incidente.id,
  }
  base.ordenes.push(orden)
  incidente.estado = 'CON_OT'
  return orden
}

/** Las mismas validaciones que RegistrarIncidenteRequest: 400 con los errores por campo. */
function validar(datos: Partial<DatosIncidente>): ErrorCampo[] {
  const errores: ErrorCampo[] = []
  const error = (campo: string, mensaje: string) => errores.push({ campo, mensaje })
  if (datos.idInstalacion == null) error('idInstalacion', 'Elegí la instalación.')
  if (!datos.origen || !ORIGENES_INCIDENTE.includes(datos.origen)) {
    error('origen', 'Indicá el origen.')
  }
  if (datos.idCategoriaFalla == null) error('idCategoriaFalla', 'Elegí la categoría de la falla.')
  if (datos.prioridad != null && !PRIORIDADES.includes(datos.prioridad)) {
    error('prioridad', 'Elegí la prioridad.')
  }
  const descripcion = datos.descripcion?.trim() ?? ''
  if (!descripcion) error('descripcion', 'Describí la falla.')
  if (descripcion.length > 2000) {
    error('descripcion', 'La descripción admite hasta 2000 caracteres.')
  }
  if ((datos.tercero ?? '').length > 150) {
    error('tercero', 'El tercero admite hasta 150 caracteres.')
  }
  if ((datos.causa ?? '').length > 500) error('causa', 'La causa admite hasta 500 caracteres.')
  return errores
}

/** 401, 403, 404 o 400 como el backend; si puede seguir, el incidente en espera. */
function buscarEnEspera(id: string | readonly string[] | undefined) {
  const rechazo = rechazoJefaturaUOperador()
  if (rechazo) return { rechazo }
  const incidente = base.incidentes.find((i) => i.id === Number(id))
  if (!incidente) {
    return {
      rechazo: problema(404, 'no-encontrado', 'Recurso no encontrado', 'No existe el incidente.'),
    }
  }
  if (incidente.estado !== 'EN_ESPERA_TERCERO') {
    return {
      rechazo: reglaNegocio(`El incidente ${incidente.numero} no está en espera de un tercero.`),
    }
  }
  return { incidente }
}

export const handlersIncidentes = [
  http.post('/api/v1/incidentes', async ({ request }) => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const datos = (await request.json()) as Partial<DatosIncidente>
    const errores = validar(datos)
    if (errores.length > 0) {
      return problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
        errores,
      })
    }
    // 3a: la instalación tiene que existir (las simuladas están todas activas).
    if (!base.instalaciones.some((i) => i.id === datos.idInstalacion)) {
      return reglaNegocio(`La instalación ${datos.idInstalacion} no existe o está inactiva.`)
    }
    const categoria = base.categorias.find((c) => c.id === datos.idCategoriaFalla && c.activa)
    if (!categoria) {
      return reglaNegocio(
        `La categoría de falla ${datos.idCategoriaFalla} no existe o está inactiva.`,
      )
    }
    const descripcion = datos.descripcion!.trim()
    // 4a: con «Otra», la descripción tiene que detallar la falla.
    if (esCategoriaOtra(categoria) && descripcion.length < MINIMO_DETALLE_OTRA) {
      return reglaNegocio(
        `Con la categoría «Otra», detallá la falla en la descripción (al menos ${MINIMO_DETALLE_OTRA} caracteres).`,
      )
    }
    // 4b: con tercero queda en espera sin OT (RegistrarIncidenteCommand.esFallaDeTercero).
    const esTercero = Boolean(datos.tercero?.trim())

    const id = Math.max(0, ...base.incidentes.map((i) => i.id)) + 1
    const incidente: IncidenteSimulado = {
      id,
      numero: `INC-${String(id).padStart(4, '0')}`,
      estado: esTercero ? 'EN_ESPERA_TERCERO' : 'ABIERTO',
      origen: datos.origen!,
      idCategoriaFalla: categoria.id,
      prioridad: datos.prioridad ?? categoria.prioridadSugerida,
      idInstalacion: datos.idInstalacion!,
      descripcion,
      tercero: esTercero ? datos.tercero!.trim() : null,
      causa: esTercero ? datos.causa?.trim() || null : null,
      creadoEn: new Date().toISOString(),
    }
    base.incidentes.push(incidente)
    const orden = esTercero ? null : crearOrden(incidente)
    guardarBase()
    return HttpResponse.json(respuesta(incidente, orden), {
      status: 201,
      headers: { Location: `/api/v1/incidentes/${id}` },
    })
  }),

  http.post('/api/v1/incidentes/:id/cerrar-sin-ot', ({ params }) => {
    const { rechazo, incidente } = buscarEnEspera(params.id)
    if (rechazo) return rechazo
    incidente.estado = 'CERRADO_SIN_OT'
    guardarBase()
    return HttpResponse.json(respuesta(incidente, null))
  }),

  http.post('/api/v1/incidentes/:id/generar-ot', ({ params }) => {
    const { rechazo, incidente } = buscarEnEspera(params.id)
    if (rechazo) return rechazo
    const orden = crearOrden(incidente)
    guardarBase()
    return HttpResponse.json(respuesta(incidente, orden))
  }),
]
