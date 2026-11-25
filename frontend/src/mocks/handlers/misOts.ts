import { http, HttpResponse } from 'msw/http'
import type { OrdenDeMiCuadrilla } from '../../features/pendientes/types'
import type { PageResponse } from '../../shared/api/paginacion'
import { base, type OrdenSimulada } from '../datos'
import { sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Bandeja «Mis OTs» (HU-07), como MisOtsService: solo el Técnico, las OTs de su cuadrilla que tiene
// entre manos. Una OT anulada, reasignada o cerrada deja de figurar porque cambia su estado o su
// cuadrilla.

const ESTADOS = ['ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']
const TAMANIO_POR_DEFECTO = 20
const TAMANIO_MAXIMO = 100

function aFila(orden: OrdenSimulada): OrdenDeMiCuadrilla {
  const pausa = orden.pausas.find((p) => p.fin === null) ?? null
  const instalacion = base.instalaciones.find((i) => i.id === orden.idInstalacion)
  const incidente = base.incidentes.find((i) => i.id === orden.idIncidente)
  const categoria = base.categorias.find((c) => c.id === incidente?.idCategoriaFalla)
  return {
    id: orden.id,
    numero: orden.numero,
    tipo: orden.idIncidente === null ? 'PREVENTIVA' : 'CORRECTIVA',
    estado: orden.estado,
    estadoAnteriorPausa: orden.estadoAnteriorPausa,
    prioridad: orden.prioridad,
    categoria: categoria?.nombre ?? null,
    numeroIncidente: incidente?.numero ?? null,
    fechaEjecucion: orden.fechaEjecucion,
    fechaLimite: orden.fechaLimite,
    slaDefinido: orden.fechaLimite !== null,
    // En pausa por un tercero el SLA no corre: no vence (VencimientoSinEntidad).
    vencida:
      pausa?.motivo !== 'TERCERO' &&
      orden.fechaLimite !== null &&
      new Date(orden.fechaLimite).getTime() < Date.now(),
    iniciadaEn: orden.iniciadaEn,
    instalacion: {
      id: orden.idInstalacion,
      codigo: instalacion?.codigo ?? '',
      nombre: instalacion?.nombre ?? '',
      direccion: instalacion?.direccion ?? null,
      rutaZona: instalacion?.rutaZona ?? null,
      latitud: instalacion?.latitud ?? null,
      longitud: instalacion?.longitud ?? null,
    },
    pausa: pausa && { motivo: pausa.motivo, detalle: pausa.detalle, inicio: pausa.inicio },
  }
}

// Sin fecha (o sin fecha límite), al final, como NULLS LAST. Las fechas ISO se ordenan como texto.
function nullsAlFinal(a: string | null, b: string | null) {
  if (a === b) return 0
  if (a === null) return 1
  if (b === null) return -1
  return a < b ? -1 : 1
}

// VencimientoSinEntidad.ORDEN_JPQL: primero las que corren, después las en pausa por un tercero
// y al final las sin SLA; dentro de cada grupo, por fecha límite.
const grupoVencimiento = (orden: OrdenSimulada) =>
  orden.pausas.some((p) => p.fin === null && p.motivo === 'TERCERO')
    ? 1
    : orden.fechaLimite === null
      ? 2
      : 0

/** Por fecha de ejecución (las atrasadas primero), por vencimiento y por creación. */
function porFechaYVencimiento(a: OrdenSimulada, b: OrdenSimulada) {
  return (
    nullsAlFinal(a.fechaEjecucion, b.fechaEjecucion) ||
    grupoVencimiento(a) - grupoVencimiento(b) ||
    nullsAlFinal(a.fechaLimite, b.fechaLimite) ||
    a.creadaEn.localeCompare(b.creadaEn) ||
    a.id - b.id
  )
}

export const handlersMisOts = [
  http.get('/api/v1/mis-ots', ({ request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'TECNICO') return sinPermiso()

    const parametros = new URL(request.url).searchParams
    const pagina = Math.max(0, Number(parametros.get('page') ?? 0) || 0)
    const tamanio = Math.min(
      TAMANIO_MAXIMO,
      Math.max(1, Number(parametros.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO),
    )
    // Un técnico sin cuadrilla recibe la página vacía.
    const filas =
      usuario.idCuadrilla === null
        ? []
        : base.ordenes
            .filter((o) => o.idCuadrilla === usuario.idCuadrilla && ESTADOS.includes(o.estado))
            .sort(porFechaYVencimiento)
            .map(aFila)
    const respuesta: PageResponse<OrdenDeMiCuadrilla> = {
      contenido: filas.slice(pagina * tamanio, (pagina + 1) * tamanio),
      pagina,
      tamanio,
      totalElementos: filas.length,
      totalPaginas: Math.ceil(filas.length / tamanio),
    }
    return HttpResponse.json(respuesta)
  }),
]
