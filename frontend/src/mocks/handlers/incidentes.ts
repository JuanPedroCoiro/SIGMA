import { http, HttpResponse } from 'msw/http'
import type { IncidenteResponse, Prioridad } from '../../features/ot/types'
import { base, guardarBase, type IncidenteSimulado, type OrdenSimulada } from '../datos'
import { problema, reglaNegocio } from '../problemas'
import { rechazoJefaturaUOperador } from './pendientes'

// Resolver la espera de un tercero (#38): cerrar sin OT o generar la OT. Jefatura y Operador.

const HORA = 3_600_000
/** Plazos de resolución de las reglas de prueba (db/datos-prueba del backend). */
const HORAS_RESOLUCION: Record<Prioridad, number> = { ALTA: 8, MEDIA: 48, BAJA: 120 }

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
    const ahora = new Date()
    const id = Math.max(0, ...base.ordenes.map((o) => o.id)) + 1
    const orden: OrdenSimulada = {
      id,
      numero: `OT-${String(id).padStart(4, '0')}`,
      estado: 'PENDIENTE',
      idCuadrilla: null,
      inicioSla: ahora.toISOString(),
      fechaLimite: new Date(
        ahora.getTime() + HORAS_RESOLUCION[incidente.prioridad] * HORA,
      ).toISOString(),
      creadaEn: ahora.toISOString(),
      estadoAnteriorPausa: null,
      pausas: [],
      prioridad: incidente.prioridad,
      idInstalacion: incidente.idInstalacion,
      numeroIncidente: incidente.numero,
    }
    base.ordenes.push(orden)
    incidente.estado = 'CON_OT'
    guardarBase()
    return HttpResponse.json(respuesta(incidente, orden))
  }),
]
