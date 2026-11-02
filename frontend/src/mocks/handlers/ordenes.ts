import { http, HttpResponse } from 'msw/http'
import type { MotivoPausa } from '../../features/ot/types'
import { TIPOS_PROBLEMA, type ErrorCampo } from '../../shared/api/problemDetail'
import { base, guardarBase, type OrdenSimulada } from '../datos'
import { problema, reglaNegocio, sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Detalle, pausar y reanudar una OT (#39). Técnico (solo su cuadrilla), Operador y Jefatura.

const MINUTO = 60_000
const PAUSABLES = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO']
const ROLES = ['JEFATURA', 'OPERADOR', 'TECNICO']

type DatosPausa = { motivo?: MotivoPausa; detalle?: string | null }

function minutos(desde: string, hasta: number) {
  return Math.floor((hasta - new Date(desde).getTime()) / MINUTO)
}

/** La OT como la devuelve OrdenTrabajoDetalle, con lo que se calcula al consultarla. */
function respuesta(orden: OrdenSimulada) {
  const ahora = Date.now()
  const abierta = orden.pausas.find((p) => p.fin === null) ?? null
  const enPausaPorTercero = abierta?.motivo === 'TERCERO'
  const cerrada = ['CERRADA_TECNICA', 'VALIDADA', 'ANULADA'].includes(orden.estado)
  const enPausaMinutos: Partial<Record<MotivoPausa, number>> = {}
  for (const pausa of orden.pausas) {
    const duracion = minutos(pausa.inicio, pausa.fin ? new Date(pausa.fin).getTime() : ahora)
    enPausaMinutos[pausa.motivo] = (enPausaMinutos[pausa.motivo] ?? 0) + duracion
  }
  const totalMinutos = minutos(orden.creadaEn, ahora)
  const pausado = Object.values(enPausaMinutos).reduce((a, b) => a + b, 0)
  return {
    id: orden.id,
    numero: orden.numero,
    tipo: 'CORRECTIVA',
    estado: orden.estado,
    idCuadrilla: orden.idCuadrilla,
    inicioSla: orden.inicioSla,
    fechaLimite: orden.fechaLimite,
    slaDefinido: orden.fechaLimite !== null,
    vencida:
      !cerrada &&
      !enPausaPorTercero &&
      orden.fechaLimite !== null &&
      new Date(orden.fechaLimite).getTime() < ahora,
    pausaAbierta: abierta,
    tiempos: cerrada
      ? null
      : { totalMinutos, enPausaMinutos, efectivoMinutos: totalMinutos - pausado },
    pausas: orden.pausas,
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
  )

export const handlersOrdenes = [
  http.get('/api/v1/ordenes/:id', ({ params }) => {
    const { rechazo, orden } = buscarAccesible(params.id)
    if (rechazo) return rechazo
    return HttpResponse.json(respuesta(orden))
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
]
