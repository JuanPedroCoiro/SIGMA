import { http, HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import type {
  EntradaHistorial,
  FichaInstalacion,
  OrdenEnCursoFicha,
} from '../../features/activos/types'
import {
  base,
  guardarBase,
  type IncidenteSimulado,
  type InstalacionSimulada,
  type OrdenSimulada,
  type UsuarioSimulado,
} from '../datos'
import { problema, sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'
import { estaVencida, puedeVerOrden } from './ordenes'

// Endpoints de instalaciones acordados con el backend (HU-02): el listado es para JEFATURA y
// OPERADOR; la importación, solo para JEFATURA. La ficha y el historial (HU-10), para todos los
// roles.

const TAMANIO_POR_DEFECTO = 20
const TAMANIO_MAXIMO = 100
const COLUMNAS = ['codigo', 'nombre', 'ruta_zona', 'latitud', 'longitud'] as const

type ErrorFila = { fila: number; campo: string; mensaje: string }

/** En minúsculas y sin tildes, como compara el backend. */
function sinTildes(texto: string) {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

/**
 * Lectura simplificada de la planilla: CSV separado por punto y coma, como el del backend, pero
 * sin campos entre comillas. Valida lo mismo que la importación real salvo el rectángulo de
 * Uruguay.
 */
function leerPlanilla(texto: string) {
  const lineas = texto.replace(/^\uFEFF/, '').split(/\r?\n/)
  const encabezados = lineas[0].split(';').map((e) => e.trim().toLowerCase())
  if (!lineas[0].trim()) {
    return {
      invalida: 'El archivo está vacío: la primera fila tiene que tener los encabezados.',
      nuevas: [],
      errores: [],
    }
  }
  const faltantes = COLUMNAS.filter((c) => !encabezados.includes(c))
  if (faltantes.length > 0) {
    return {
      invalida: `Faltan las columnas ${faltantes.join(', ')}. La primera fila tiene que tener: ${COLUMNAS.join(';')}.`,
      nuevas: [],
      errores: [],
    }
  }
  const errores: ErrorFila[] = []
  const nuevas: Omit<InstalacionSimulada, 'id'>[] = []
  const codigos = new Set(base.instalaciones.map((i) => i.codigo))

  lineas.slice(1).forEach((linea, i) => {
    if (!linea.trim()) return
    const fila = i + 2
    const campos = linea.split(';').map((c) => c.trim())
    const valor = (columna: string) => campos[encabezados.indexOf(columna)] ?? ''
    for (const columna of COLUMNAS) {
      if (!valor(columna))
        errores.push({ fila, campo: columna, mensaje: `Falta el valor de ${columna}.` })
    }
    const [latitud, longitud] = (['latitud', 'longitud'] as const).map((columna) => {
      const numero = Number(valor(columna).replace(',', '.'))
      if (valor(columna) && Number.isNaN(numero)) {
        errores.push({
          fila,
          campo: columna,
          mensaje: `«${valor(columna)}» no es un número: usá grados decimales, por ejemplo -34,901234.`,
        })
      }
      return numero
    })
    const codigo = valor('codigo')
    if (codigo && codigos.has(codigo)) {
      errores.push({
        fila,
        campo: 'codigo',
        mensaje: `Ya existe una instalación con el código ${codigo}.`,
      })
    }
    codigos.add(codigo)
    nuevas.push({
      codigo,
      nombre: valor('nombre'),
      rutaZona: valor('ruta_zona'),
      latitud,
      longitud,
    })
  })
  return { invalida: null, nuevas, errores }
}

/**
 * Contenido del campo `archivo` del multipart. Se lee el cuerpo como texto porque en los tests
 * `request.formData()` de Node rechaza el File de jsdom.
 */
async function textoDelArchivo(request: Request) {
  const limite = /boundary=(.+)$/.exec(request.headers.get('Content-Type') ?? '')?.[1]
  if (!limite) return ''
  const parte = (await request.text())
    .split(`--${limite}`)
    .find((p) => p.includes('name="archivo"'))
  if (!parte) return ''
  return parte.slice(parte.indexOf('\r\n\r\n') + 4).replace(/\r\n$/, '')
}

/** Como EstadoOT.EN_CURSO. */
const EN_CURSO = ['PENDIENTE', 'ASIGNADA', 'EN_PROCESO', 'EN_PAUSA']

/** La simulada sin reglas de SLA es de otro organismo; las demás, de MTOP. */
function organismoDe(instalacion: InstalacionSimulada) {
  return instalacion.sinReglaSla
    ? { id: 2, nombre: 'Organismo de prueba sin SLA' }
    : { id: 1, nombre: 'MTOP' }
}

/** Primero la que vence antes; sin SLA, al final; a igual fecha, la más vieja (FichaInstalacionService). */
function porVencimiento(a: OrdenSimulada, b: OrdenSimulada) {
  const limite = (o: OrdenSimulada) =>
    o.fechaLimite === null ? Number.POSITIVE_INFINITY : new Date(o.fechaLimite).getTime()
  return (
    limite(a) - limite(b) ||
    new Date(a.creadaEn).getTime() - new Date(b.creadaEn).getTime() ||
    a.id - b.id
  )
}

function enCurso(orden: OrdenSimulada, puedeVerDetalle: boolean): OrdenEnCursoFicha {
  const cuadrilla = base.cuadrillas.find((c) => c.id === orden.idCuadrilla)
  const incidente = base.incidentes.find((i) => i.id === orden.idIncidente)
  return {
    id: orden.id,
    numero: orden.numero,
    tipo: orden.idIncidente === null ? 'PREVENTIVA' : 'CORRECTIVA',
    estado: orden.estado,
    prioridad: orden.prioridad,
    fechaLimite: orden.fechaLimite,
    slaDefinido: orden.fechaLimite !== null,
    vencida: estaVencida(orden),
    cuadrilla: cuadrilla ? { id: cuadrilla.id, nombre: cuadrilla.nombre } : null,
    numeroIncidente: incidente?.numero ?? null,
    creadoEn: orden.creadaEn,
    puedeVerDetalle,
  }
}

/** Como EstadoOT.ESTADOS_HISTORIAL y EstadoIncidente.SIN_OT_EN_HISTORIAL. */
const OTS_EN_HISTORIAL = ['CERRADA_TECNICA', 'VALIDADA', 'ANULADA']
export const INCIDENTES_EN_HISTORIAL = ['EN_ESPERA_TERCERO', 'CERRADO_SIN_OT']

function datosDelIncidente(incidente: IncidenteSimulado | undefined) {
  if (!incidente) return null
  return {
    id: incidente.id,
    numero: incidente.numero,
    categoria: base.categorias.find((c) => c.id === incidente.idCategoriaFalla)?.nombre ?? '',
    prioridad: incidente.prioridad,
    descripcion: incidente.descripcion,
  }
}

const SIN_DATOS_DE_OT = {
  estadoOt: null,
  tipoOt: null,
  iniciadaEn: null,
  cerradaEn: null,
  cuadrilla: null,
  diagnostico: null,
  solucion: null,
  observaciones: null,
  motivoAnulacion: null,
}

function cantidadFotosIncidente(idIncidente: number | null) {
  return idIncidente === null ? 0 : base.fotos.filter((f) => f.idIncidente === idIncidente).length
}

/** Las entradas del historial de la instalación, de lo más nuevo a lo más viejo (HU-10). */
function historialDe(idInstalacion: number, usuario: UsuarioSimulado): EntradaHistorial[] {
  const ordenes: EntradaHistorial[] = base.ordenes
    .filter((o) => o.idInstalacion === idInstalacion && OTS_EN_HISTORIAL.includes(o.estado))
    .map((o) => {
      const incidente = base.incidentes.find((i) => i.id === o.idIncidente)
      const cuadrilla = base.cuadrillas.find((c) => c.id === o.idCuadrilla)
      return {
        clase: 'OT',
        id: o.id,
        numero: o.numero,
        estadoOt: o.estado,
        estadoIncidente: null,
        tipoOt: o.idIncidente === null ? 'PREVENTIVA' : 'CORRECTIVA',
        // La anulada, por su fecha de anulación (en el backend, modificado_en).
        fecha: (o.estado === 'ANULADA' ? o.anuladaEn : o.cierre?.cerradaEn) ?? o.creadaEn,
        reportadoEn: incidente?.creadoEn ?? o.creadaEn,
        iniciadaEn: o.iniciadaEn,
        cerradaEn: o.cierre?.cerradaEn ?? null,
        cuadrilla: cuadrilla ? { id: cuadrilla.id, nombre: cuadrilla.nombre } : null,
        incidente: datosDelIncidente(incidente),
        diagnostico: o.cierre?.diagnostico ?? null,
        solucion: o.cierre?.solucion ?? null,
        observaciones: o.cierre?.observaciones ?? null,
        motivoAnulacion: o.motivoAnulacion ?? null,
        tercero: null,
        causa: null,
        fotosIncidente: cantidadFotosIncidente(o.idIncidente),
        fotosCierre: base.fotosCierre.filter((f) => f.idOrdenTrabajo === o.id).length,
        puedeVerDetalle: puedeVerOrden(usuario, o),
      }
    })
  const incidentes: EntradaHistorial[] = base.incidentes
    .filter((i) => i.idInstalacion === idInstalacion && INCIDENTES_EN_HISTORIAL.includes(i.estado))
    .map((i) => ({
      ...SIN_DATOS_DE_OT,
      clase: 'INCIDENTE',
      id: i.id,
      numero: i.numero,
      estadoIncidente: i.estado,
      fecha: i.creadoEn,
      reportadoEn: i.creadoEn,
      incidente: datosDelIncidente(i),
      tercero: i.tercero,
      causa: i.causa,
      fotosIncidente: cantidadFotosIncidente(i.id),
      fotosCierre: 0,
      puedeVerDetalle: false,
    }))
  // A igual fecha, las OTs antes que los incidentes y la más nueva primero, como el backend.
  return [...ordenes, ...incidentes].sort(
    (a, b) =>
      new Date(b.fecha).getTime() - new Date(a.fecha).getTime() ||
      b.clase.localeCompare(a.clase) ||
      b.id - a.id,
  )
}

export const handlersInstalaciones = [
  // Historial (HU-10, CU-8): todos los roles, también las inactivas. Paginado, en orden fijo.
  http.get('/api/v1/instalaciones/:id/historial', ({ params, request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (!['JEFATURA', 'OPERADOR', 'TECNICO'].includes(usuario.rol)) return sinPermiso()
    const instalacion = base.instalaciones.find((i) => i.id === Number(params.id))
    if (!instalacion) {
      return problema(
        404,
        'no-encontrado',
        'Recurso no encontrado',
        `No existe la instalación ${String(params.id)}.`,
      )
    }
    const url = new URL(request.url)
    const pagina = Math.max(0, Number(url.searchParams.get('page') ?? 0))
    const tamanio = Math.min(
      TAMANIO_MAXIMO,
      Number(url.searchParams.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO,
    )
    const entradas = historialDe(instalacion.id, usuario)
    return HttpResponse.json({
      contenido: entradas.slice(pagina * tamanio, (pagina + 1) * tamanio),
      pagina,
      tamanio,
      totalElementos: entradas.length,
      totalPaginas: Math.ceil(entradas.length / tamanio),
    })
  }),

  // Ficha (HU-10): todos los roles, también las inactivas. Un técnico solo puede abrir las OTs en
  // curso de su cuadrilla, como AccesoOt.
  http.get('/api/v1/instalaciones/:id/ficha', ({ params }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (!['JEFATURA', 'OPERADOR', 'TECNICO'].includes(usuario.rol)) return sinPermiso()
    const instalacion = base.instalaciones.find((i) => i.id === Number(params.id))
    if (!instalacion) {
      return problema(
        404,
        'no-encontrado',
        'Recurso no encontrado',
        `No existe la instalación ${String(params.id)}.`,
      )
    }
    const puedeVer = (o: OrdenSimulada) => puedeVerOrden(usuario, o)
    const ficha: FichaInstalacion = {
      id: instalacion.id,
      codigo: instalacion.codigo,
      nombre: instalacion.nombre,
      rutaZona: instalacion.rutaZona,
      latitud: instalacion.latitud,
      longitud: instalacion.longitud,
      tipo: instalacion.tipo ?? null,
      direccion: instalacion.direccion ?? null,
      activa: instalacion.activa ?? true,
      organismo: organismoDe(instalacion),
      ordenesEnCurso: base.ordenes
        .filter((o) => o.idInstalacion === instalacion.id && EN_CURSO.includes(o.estado))
        .sort(porVencimiento)
        .map((o) => enCurso(o, puedeVer(o))),
    }
    return HttpResponse.json(ficha)
  }),

  // Antes del listado: las activas con coordenadas, sin paginar, para el mapa (#70). Las
  // simuladas tienen coordenadas ficticias alrededor de Montevideo.
  http.get('/api/v1/instalaciones/mapa', () => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'JEFATURA' && usuario.rol !== 'OPERADOR') return sinPermiso()
    return HttpResponse.json(
      base.instalaciones
        .filter((i) => i.activa !== false)
        .sort((a, b) => a.codigo.localeCompare(b.codigo)),
    )
  }),

  http.get('/api/v1/instalaciones', ({ request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'JEFATURA' && usuario.rol !== 'OPERADOR') return sinPermiso()
    const url = new URL(request.url)
    const pagina = Math.max(0, Number(url.searchParams.get('page') ?? 0))
    const tamanio = Math.min(
      TAMANIO_MAXIMO,
      Number(url.searchParams.get('size') ?? TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO,
    )
    // Con `buscar`, solo las activas, por código o nombre sin distinguir mayúsculas ni tildes
    // (#40).
    const buscar = sinTildes(url.searchParams.get('buscar')?.trim() ?? '')
    const ordenadas = base.instalaciones
      .filter(
        (i) =>
          !buscar || (i.activa !== false && sinTildes(`${i.codigo} ${i.nombre}`).includes(buscar)),
      )
      .sort((a, b) => a.codigo.localeCompare(b.codigo))
    return HttpResponse.json({
      contenido: ordenadas.slice(pagina * tamanio, (pagina + 1) * tamanio),
      pagina,
      tamanio,
      totalElementos: ordenadas.length,
      totalPaginas: Math.ceil(ordenadas.length / tamanio),
    })
  }),

  http.post('/api/v1/instalaciones/importar', async ({ request }) => {
    const usuario = usuarioEnSesion()
    if (!usuario) return sinSesion()
    if (usuario.rol !== 'JEFATURA') return sinPermiso()
    const { invalida, nuevas, errores } = leerPlanilla(await textoDelArchivo(request))

    // El archivo entero no sirve: 422 sin errores por fila.
    if (invalida) {
      return problema(422, TIPOS_PROBLEMA.planillaInvalida, 'Planilla inválida', invalida, {
        errores: [],
      })
    }

    if (errores.length > 0) {
      return problema(
        422,
        TIPOS_PROBLEMA.planillaInvalida,
        'Planilla con errores',
        `La planilla tiene ${errores.length} errores. No se importó ninguna instalación: corregí la planilla y volvé a subirla.`,
        { errores },
      )
    }
    let id = Math.max(0, ...base.instalaciones.map((i) => i.id))
    base.instalaciones.push(...nuevas.map((nueva) => ({ id: ++id, ...nueva })))
    guardarBase()
    return HttpResponse.json({ importadas: nuevas.length, errores: [] })
  }),
]
