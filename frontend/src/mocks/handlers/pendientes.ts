import { http, HttpResponse } from 'msw/http'
import type { Prioridad } from '../../features/ot/types'
import type {
  CriterioOrden,
  IncidenteEnEspera,
  OrdenPendiente,
} from '../../features/pendientes/types'
import { base, type IncidenteSimulado, type OrdenSimulada } from '../datos'
import { sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Tablero de pendientes (#43). Jefatura y Operador, como el backend.

const ABIERTAS = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']
const RANGO_PRIORIDAD: Record<Prioridad, number> = { ALTA: 0, MEDIA: 1, BAJA: 2 }
const TAMANIO_POR_DEFECTO = 20

/** 401 o 403 como el backend; null si puede seguir. También lo usan los handlers de incidentes. */
export function rechazoJefaturaUOperador() {
  const usuario = usuarioEnSesion()
  if (!usuario) return sinSesion()
  if (usuario.rol !== 'JEFATURA' && usuario.rol !== 'OPERADOR') return sinPermiso()
  return null
}

function aFila(orden: OrdenSimulada): OrdenPendiente {
  const pausa = orden.pausas.find((p) => p.fin === null) ?? null
  const instalacion = base.instalaciones.find((i) => i.id === orden.idInstalacion)
  const cuadrilla = base.cuadrillas.find((c) => c.id === orden.idCuadrilla)
  return {
    id: orden.id,
    numero: orden.numero,
    tipo: 'CORRECTIVA',
    estado: orden.estado,
    prioridad: orden.prioridad,
    fechaLimite: orden.fechaLimite,
    slaDefinido: orden.fechaLimite !== null,
    vencida:
      pausa?.motivo !== 'TERCERO' &&
      orden.fechaLimite !== null &&
      new Date(orden.fechaLimite).getTime() < Date.now(),
    codigoInstalacion: instalacion?.codigo ?? '',
    nombreInstalacion: instalacion?.nombre ?? '',
    cuadrilla: cuadrilla?.nombre ?? null,
    numeroIncidente: orden.numeroIncidente,
    pausa: pausa && { motivo: pausa.motivo, detalle: pausa.detalle, inicio: pausa.inicio },
    creadoEn: orden.creadaEn,
  }
}

// El mismo orden que PendientesService, con los mismos desempates.
const porCreacion = (a: OrdenPendiente, b: OrdenPendiente) =>
  a.creadoEn.localeCompare(b.creadoEn) || a.id - b.id
// Sin fecha límite, al final (como NULLS LAST en PostgreSQL). Las fechas ISO en UTC se ordenan
// bien como texto.
function porFechaLimite(a: OrdenPendiente, b: OrdenPendiente) {
  if (a.fechaLimite === b.fechaLimite) return 0
  if (a.fechaLimite === null) return 1
  if (b.fechaLimite === null) return -1
  return a.fechaLimite < b.fechaLimite ? -1 : 1
}
const grupoVencimiento = (fila: OrdenPendiente) =>
  fila.pausa?.motivo === 'TERCERO' ? 1 : fila.fechaLimite === null ? 2 : 0
const rangoPrioridad = (fila: OrdenPendiente) =>
  fila.prioridad ? RANGO_PRIORIDAD[fila.prioridad] : 3

const COMPARADORES: Record<CriterioOrden, (a: OrdenPendiente, b: OrdenPendiente) => number> = {
  VENCIMIENTO: (a, b) =>
    grupoVencimiento(a) - grupoVencimiento(b) || porFechaLimite(a, b) || porCreacion(a, b),
  PRIORIDAD: (a, b) =>
    rangoPrioridad(a) - rangoPrioridad(b) || porFechaLimite(a, b) || porCreacion(a, b),
  CREACION: porCreacion,
}

function aIncidente(incidente: IncidenteSimulado): IncidenteEnEspera {
  const instalacion = base.instalaciones.find((i) => i.id === incidente.idInstalacion)
  return {
    id: incidente.id,
    numero: incidente.numero,
    prioridad: incidente.prioridad,
    codigoInstalacion: instalacion?.codigo ?? '',
    nombreInstalacion: instalacion?.nombre ?? '',
    descripcion: incidente.descripcion,
    tercero: incidente.tercero ?? '',
    causa: incidente.causa,
    creadoEn: incidente.creadoEn,
  }
}

export const handlersPendientes = [
  http.get('/api/v1/pendientes/ordenes', ({ request }) => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const url = new URL(request.url)
    const criterio = (url.searchParams.get('orden') ?? 'VENCIMIENTO') as CriterioOrden
    const pagina = Math.max(0, Number(url.searchParams.get('page') ?? 0))
    const tamanio =
      Number(url.searchParams.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO
    const filas = base.ordenes
      .filter((o) => ABIERTAS.includes(o.estado))
      .map(aFila)
      .sort(COMPARADORES[criterio])
    return HttpResponse.json({
      contenido: filas.slice(pagina * tamanio, (pagina + 1) * tamanio),
      pagina,
      tamanio,
      totalElementos: filas.length,
      totalPaginas: Math.ceil(filas.length / tamanio),
    })
  }),

  http.get('/api/v1/pendientes/incidentes-en-espera', () => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    return HttpResponse.json(
      base.incidentes
        .filter((i) => i.estado === 'EN_ESPERA_TERCERO')
        .sort((a, b) => a.creadoEn.localeCompare(b.creadoEn))
        .map(aIncidente),
    )
  }),
]
