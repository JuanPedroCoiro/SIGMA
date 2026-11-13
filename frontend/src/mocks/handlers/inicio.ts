import { http, HttpResponse } from 'msw/http'
import type { ResumenInicio } from '../../features/pendientes/types'
import { hoyEnMontevideo } from '../../shared/lib/fechas'
import { base } from '../datos'
import { sinSesion, usuarioEnSesion } from '../problemas'

// Resumen del inicio por rol (#72): las mismas cuentas que ResumenInicioService.

const HORA = 3_600_000
const ABIERTAS = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']
const DE_LA_CUADRILLA = ['ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']

const SIN_TARJETAS: ResumenInicio = {
  pendientesDeAsignar: null,
  vencidas: null,
  porVencer: null,
  enPausa: null,
  incidentesEnEspera: null,
  incidentesHoy: null,
  otsDeHoy: null,
}

/** Abiertas con la fecha límite en [desde, hasta), sin las pausadas por un tercero (no vencen). */
function conLimiteEntre(desde: number, hasta: number) {
  return base.ordenes.filter((o) => {
    if (!ABIERTAS.includes(o.estado) || o.fechaLimite === null) return false
    if (o.pausas.some((p) => p.fin === null && p.motivo === 'TERCERO')) return false
    const limite = new Date(o.fechaLimite).getTime()
    return limite >= desde && limite < hasta
  }).length
}

const contarOts = (estado: string) => base.ordenes.filter((o) => o.estado === estado).length

const incidentesEnEspera = () =>
  base.incidentes.filter((i) => i.estado === 'EN_ESPERA_TERCERO').length

export const handlersInicio = [
  http.get('/api/v1/inicio/resumen', () => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    const ahora = Date.now()
    const hoy = hoyEnMontevideo()
    switch (usuario.rol) {
      case 'JEFATURA':
        return HttpResponse.json({
          ...SIN_TARJETAS,
          pendientesDeAsignar: contarOts('PENDIENTE'),
          vencidas: conLimiteEntre(0, ahora),
          porVencer: conLimiteEntre(ahora, ahora + 24 * HORA),
          enPausa: contarOts('EN_PAUSA'),
          incidentesEnEspera: incidentesEnEspera(),
        })
      case 'OPERADOR':
        return HttpResponse.json({
          ...SIN_TARJETAS,
          vencidas: conLimiteEntre(0, ahora),
          incidentesEnEspera: incidentesEnEspera(),
          incidentesHoy: base.incidentes.filter(
            (i) => hoyEnMontevideo(new Date(i.creadoEn)) === hoy,
          ).length,
        })
      case 'TECNICO':
        return HttpResponse.json({
          ...SIN_TARJETAS,
          otsDeHoy: base.ordenes.filter(
            (o) =>
              o.idCuadrilla === usuario.idCuadrilla &&
              o.fechaEjecucion === hoy &&
              DE_LA_CUADRILLA.includes(o.estado),
          ).length,
        })
      default:
        return HttpResponse.json(SIN_TARJETAS)
    }
  }),
]
