import type {
  CierreTecnico,
  EstadoIncidente,
  EstadoOT,
  MomentoFoto,
  MotivoPausa,
  OrigenIncidente,
  PausaOT,
  Prioridad,
} from '../features/ot/types'
import { hoyEnMontevideo } from '../shared/lib/fechas'
import type { Rol } from '../shared/lib/roles'

// Datos FICTICIOS de la API simulada (solo `npm run dev:simulado` y los tests).
// Se guardan en sessionStorage para que una recarga del navegador no los pierda.

export const CONTRASENA_SIMULADA = 'Prueba.1234'

export type UsuarioSimulado = {
  id: number
  nombre: string
  correo: string
  rol: Rol
  idCuadrilla: number | null
  activo: boolean
  password: string
  /** Si es true, el login responde 423 (bloqueo temporal, CU-12 4c). */
  bloqueado: boolean
}

export type CuadrillaSimulada = {
  id: number
  nombre: string
  zona: string | null
  activa: boolean
}

export type InstalacionSimulada = {
  id: number
  codigo: string
  nombre: string
  rutaZona: string
  /**
   * Ficticia, para «Mis OTs» (HU-07) y la ficha (HU-10). Las importadas de una planilla no la
   * traen: sin cargar, la ficha dice «Sin datos» (2b).
   */
  direccion?: string | null
  latitud: number
  longitud: number
  /** La instalación es de un organismo sin reglas de SLA: su OT queda «SLA sin definir» (8a). */
  sinReglaSla?: boolean
  /** Se completa después de la importación (HU-10, 2b): sin cargar, la ficha dice «Sin datos». */
  tipo?: string | null
  /** Sin el campo, activa. */
  activa?: boolean
}

/** Se guardan solo los ids: los nombres se buscan al responder, como hace el backend. */
export type PausaSimulada = Omit<PausaOT, 'nombreUsuarioPausa' | 'nombreUsuarioReanudacion'>

export type OrdenSimulada = {
  id: number
  numero: string
  estado: EstadoOT
  idCuadrilla: number | null
  /** «2026-11-12»; null si no está asignada (#41). */
  fechaEjecucion: string | null
  inicioSla: string | null
  fechaLimite: string | null
  creadaEn: string
  estadoAnteriorPausa: EstadoOT | null
  pausas: PausaSimulada[]
  prioridad: Prioridad | null
  idInstalacion: number
  /** El incidente que la originó; null si es preventiva. */
  idIncidente: number | null
  /** Cuándo la inició el técnico (HU-07); null si no empezó. */
  iniciadaEn: string | null
  /** El cierre técnico con su identificador de operación (R8); null si no se cerró. */
  cierre: CierreSimulado | null
  /** Anulación, para el historial (HU-10). En el backend la fecha es modificado_en. */
  motivoAnulacion?: string
  anuladaEn?: string
}

export type CierreSimulado = CierreTecnico & { idOperacion: string }

export type IncidenteSimulado = {
  id: number
  numero: string
  estado: EstadoIncidente
  origen: OrigenIncidente
  idCategoriaFalla: number
  prioridad: Prioridad
  idInstalacion: number
  descripcion: string
  tercero: string | null
  causa: string | null
  creadoEn: string
}

/** Los datos de una foto del incidente (#71). El contenido queda en memoria (handlers/fotos.ts). */
export type FotoSimulada = {
  id: number
  idIncidente: number
  nombreOriginal: string
  tipo: string
  tamanioBytes: number
  subidaEn: string
}

/** Una foto del cierre técnico (#90). Sin contenido: la imagen es un marcador. */
export type FotoCierreSimulada = Omit<FotoSimulada, 'idIncidente'> & {
  idOrdenTrabajo: number
  momento: MomentoFoto
}

type Base = {
  usuarios: UsuarioSimulado[]
  cuadrillas: CuadrillaSimulada[]
  instalaciones: InstalacionSimulada[]
  ordenes: OrdenSimulada[]
  incidentes: IncidenteSimulado[]
  categorias: CategoriaSimulada[]
  fotos: FotoSimulada[]
  fotosCierre: FotoCierreSimulada[]
  /** Usuario con la sesión abierta, o null. */
  idSesion: number | null
}

// Con versión: cuando cambia la forma de los datos se sube, y la base vieja guardada en la
// pestaña se descarta en lugar de mezclarse con la nueva (#61). v3: fechaEjecucion (#41). v4:
// categorías de falla e instalación sin regla de SLA (#40). v5: detalle de OT con instalación,
// cuadrilla e incidente (#57). v6: fotos del incidente (#71). v7: inicio y cierre técnico, y
// dirección de la instalación (HU-07). v8: tipo y activa de la instalación (HU-10, #92). v9: OTs
// terminadas e incidente cerrado sin OT para el historial de la instalación (HU-10, #92).
const CLAVE_ALMACEN = 'sigma.apiSimulada.v9'

const NOMBRES = ['Ana', 'Bruno', 'Carla', 'Diego', 'Elena', 'Fabián', 'Gabriela', 'Hernán']
const APELLIDOS = ['Acosta', 'Benítez', 'Cabrera', 'Duarte']

function usuario(
  id: number,
  nombre: string,
  correo: string,
  rol: Rol,
  idCuadrilla: number | null,
  extra: Partial<UsuarioSimulado> = {},
): UsuarioSimulado {
  return {
    id,
    nombre,
    correo,
    rol,
    idCuadrilla,
    activo: true,
    password: CONTRASENA_SIMULADA,
    bloqueado: false,
    ...extra,
  }
}

const RUTAS = ['Ruta 1', 'Ruta 5', 'Ruta 8', 'Zona Este', 'Zona Oeste']

/** Sin regla de SLA para su organismo: para probar «SLA sin definir» (CU-1 8a). */
export const ID_INSTALACION_SIN_SLA = 25

/** 25 cruces ficticios alrededor de Montevideo, para probar la paginación del listado. */
function crearInstalaciones(): InstalacionSimulada[] {
  return Array.from({ length: 25 }, (_, i) => {
    const numero = i + 1
    const sinReglaSla = numero === ID_INSTALACION_SIN_SLA
    return {
      id: numero,
      codigo: `INST-${String(numero).padStart(3, '0')}`,
      nombre: sinReglaSla ? 'Cruce Peñarol (sin regla de SLA)' : `Cruce Ficticio ${numero}`,
      rutaZona: RUTAS[i % RUTAS.length],
      // INST-002 sin dirección: la ficha muestra «Sin datos» (HU-10, 2b).
      direccion: numero === 2 ? null : `Calle Ficticia ${numero * 100} esq. Avenida de Prueba`,
      latitud: Number((-34.9 + i * 0.012345).toFixed(6)),
      longitud: Number((-56.2 + i * 0.023456).toFixed(6)),
      ...(sinReglaSla && { sinReglaSla }),
      // Solo la primera tiene tipo: las demás muestran «Sin datos» en la ficha.
      ...(numero === 1 && { tipo: 'Semáforo vehicular' }),
    }
  })
}

export type CategoriaSimulada = {
  id: number
  nombre: string
  prioridadSugerida: Prioridad
  activa: boolean
}

/** Las mismas de db/datos-prueba del backend, más una inactiva que no se ofrece. */
function crearCategorias(): CategoriaSimulada[] {
  const categoria = (id: number, nombre: string, prioridadSugerida: Prioridad, activa = true) => ({
    id,
    nombre,
    prioridadSugerida,
    activa,
  })
  return [
    categoria(1, 'Semáforo apagado', 'ALTA'),
    categoria(2, 'Lámpara quemada', 'MEDIA'),
    categoria(3, 'Falla de sincronización', 'MEDIA'),
    categoria(4, 'Pulsador peatonal roto', 'BAJA'),
    categoria(5, 'Daño en estructura', 'BAJA'),
    categoria(6, 'Otra', 'MEDIA'),
    categoria(7, 'Categoría dada de baja', 'BAJA', false),
  ]
}

const HORA = 3_600_000

function haceHoras(horas: number) {
  return new Date(Date.now() - horas * HORA).toISOString()
}

function pausaAbierta(motivo: MotivoPausa, detalle: string | null, horas: number): PausaSimulada {
  return {
    motivo,
    detalle,
    inicio: haceHoras(horas),
    fin: null,
    idUsuarioPausa: 3,
    idUsuarioReanudacion: null,
  }
}

/**
 * OTs correctivas ficticias (#39): una asignada a Norte para hoy, una en pausa por un tercero
 * (también de Norte, para hoy) y una pendiente sin SLA.
 */
function crearOrdenes(): OrdenSimulada[] {
  return [
    {
      id: 1,
      numero: 'OT-0001',
      estado: 'ASIGNADA',
      idCuadrilla: 1,
      fechaEjecucion: hoyEnMontevideo(),
      inicioSla: haceHoras(2),
      fechaLimite: new Date(Date.now() + 46 * HORA).toISOString(),
      creadaEn: haceHoras(2),
      estadoAnteriorPausa: null,
      pausas: [],
      prioridad: 'MEDIA',
      idInstalacion: 1,
      idIncidente: 1,
      iniciadaEn: null,
      cierre: null,
    },
    {
      id: 2,
      numero: 'OT-0002',
      estado: 'EN_PAUSA',
      idCuadrilla: 1,
      fechaEjecucion: hoyEnMontevideo(),
      inicioSla: haceHoras(30),
      fechaLimite: haceHoras(6),
      creadaEn: haceHoras(30),
      estadoAnteriorPausa: 'ASIGNADA',
      pausas: [pausaAbierta('TERCERO', 'UTE', 10)],
      prioridad: 'ALTA',
      idInstalacion: 2,
      idIncidente: 2,
      iniciadaEn: null,
      cierre: null,
    },
    {
      id: 3,
      numero: 'OT-0003',
      estado: 'PENDIENTE',
      idCuadrilla: null,
      fechaEjecucion: null,
      inicioSla: haceHoras(1),
      fechaLimite: null,
      creadaEn: haceHoras(1),
      estadoAnteriorPausa: null,
      pausas: [],
      prioridad: 'MEDIA',
      idInstalacion: 3,
      idIncidente: 3,
      iniciadaEn: null,
      cierre: null,
    },
    // Historial de INST-001 (HU-10): una cerrada técnicamente, una validada y una preventiva
    // anulada, de días anteriores.
    terminada(4, 'CERRADA_TECNICA', 5, 72, {
      iniciadaEn: haceHoras(71),
      cierre: {
        cerradaEn: haceHoras(70),
        diagnostico: 'Cable de alimentación cortado',
        solucion: 'Se reemplazó el tramo de cable',
        observaciones: null,
        idOperacion: 'historial-ot-4',
      },
    }),
    terminada(5, 'VALIDADA', 6, 240, {
      iniciadaEn: haceHoras(238),
      cierre: {
        cerradaEn: haceHoras(236),
        diagnostico: 'Lámpara verde quemada',
        solucion: 'Cambio de lámpara',
        observaciones: 'Quedó funcionando',
        idOperacion: 'historial-ot-5',
      },
    }),
    terminada(6, 'ANULADA', null, 120, {
      motivoAnulacion: 'Duplicada: misma falla que OT-0004',
      anuladaEn: haceHoras(119),
    }),
  ]
}

/** Una OT terminada de INST-001, de la cuadrilla Norte, generada hace `horas` horas. */
function terminada(
  id: number,
  estado: EstadoOT,
  idIncidente: number | null,
  horas: number,
  datos: Partial<OrdenSimulada>,
): OrdenSimulada {
  return {
    id,
    numero: `OT-${String(id).padStart(4, '0')}`,
    estado,
    idCuadrilla: 1,
    fechaEjecucion: null,
    inicioSla: idIncidente === null ? null : haceHoras(horas),
    fechaLimite: null,
    creadaEn: haceHoras(horas),
    estadoAnteriorPausa: null,
    pausas: [],
    prioridad: idIncidente === null ? null : 'MEDIA',
    idInstalacion: 1,
    idIncidente,
    iniciadaEn: null,
    cierre: null,
    ...datos,
  }
}

/** Los incidentes de OT-0001 a OT-0003 (con OT) y uno en espera de un tercero (#38). */
function crearIncidentes(): IncidenteSimulado[] {
  return [
    {
      id: 1,
      numero: 'INC-0001',
      estado: 'CON_OT',
      origen: 'RECLAMO_ORGANISMO',
      idCategoriaFalla: 2,
      prioridad: 'MEDIA',
      idInstalacion: 1,
      descripcion: 'Lámpara roja quemada en el semáforo vehicular norte',
      tercero: null,
      causa: null,
      creadoEn: haceHoras(2),
    },
    {
      id: 2,
      numero: 'INC-0002',
      estado: 'CON_OT',
      origen: 'MONITOREO',
      idCategoriaFalla: 1,
      prioridad: 'ALTA',
      idInstalacion: 2,
      descripcion: 'El cruce dejó de reportar en el monitoreo',
      tercero: null,
      causa: null,
      creadoEn: haceHoras(30),
    },
    {
      id: 3,
      numero: 'INC-0003',
      estado: 'CON_OT',
      origen: 'RECLAMO_ORGANISMO',
      idCategoriaFalla: 3,
      prioridad: 'MEDIA',
      idInstalacion: 3,
      descripcion: 'Los semáforos del cruce no están sincronizados',
      tercero: null,
      causa: null,
      creadoEn: haceHoras(1),
    },
    {
      id: 4,
      numero: 'INC-0004',
      estado: 'EN_ESPERA_TERCERO',
      origen: 'MONITOREO',
      idCategoriaFalla: 1,
      prioridad: 'MEDIA',
      idInstalacion: 4,
      descripcion: 'Semáforo apagado',
      tercero: 'UTE',
      causa: 'Corte de energía en la zona',
      creadoEn: haceHoras(3),
    },
    // Los de las OTs terminadas OT-0004 y OT-0005, y uno de INST-004 cerrado sin OT (HU-10).
    {
      id: 5,
      numero: 'INC-0005',
      estado: 'CON_OT',
      origen: 'MONITOREO',
      idCategoriaFalla: 5,
      prioridad: 'MEDIA',
      idInstalacion: 1,
      descripcion: 'El cruce quedó sin energía',
      tercero: null,
      causa: null,
      creadoEn: haceHoras(72),
    },
    {
      id: 6,
      numero: 'INC-0006',
      estado: 'CON_OT',
      origen: 'RECLAMO_ORGANISMO',
      idCategoriaFalla: 2,
      prioridad: 'MEDIA',
      idInstalacion: 1,
      descripcion: 'Lámpara verde quemada en el semáforo peatonal',
      tercero: null,
      causa: null,
      creadoEn: haceHoras(240),
    },
    {
      id: 7,
      numero: 'INC-0007',
      estado: 'CERRADO_SIN_OT',
      origen: 'MONITOREO',
      idCategoriaFalla: 1,
      prioridad: 'ALTA',
      idInstalacion: 4,
      descripcion: 'Semáforo apagado',
      tercero: 'UTE',
      causa: 'Corte programado',
      creadoEn: haceHoras(96),
    },
  ]
}

// Fotos solo en entradas del historial (HU-10): las del incidente y del cierre de OT-0005 validada
// y la de INC-0004 en espera de tercero. Sin contenido: la imagen es el marcador.
function crearFotos(): FotoSimulada[] {
  const foto = (id: number, idIncidente: number, nombreOriginal: string, horas: number) => ({
    id,
    idIncidente,
    nombreOriginal,
    tipo: 'image/jpeg',
    tamanioBytes: 180_000,
    subidaEn: haceHoras(horas),
  })
  return [
    foto(1, 6, 'semaforo-peatonal.jpg', 240),
    foto(2, 6, 'lampara-verde.jpg', 240),
    foto(3, 4, 'cruce-sin-energia.jpg', 3),
  ]
}

function crearFotosCierre(): FotoCierreSimulada[] {
  const foto = (id: number, momento: MomentoFoto, nombreOriginal: string) => ({
    id,
    idOrdenTrabajo: 5,
    momento,
    nombreOriginal,
    tipo: 'image/jpeg',
    tamanioBytes: 150_000,
    subidaEn: haceHoras(236),
  })
  return [
    foto(1, 'ANTES', 'antes.jpg'),
    foto(2, 'DURANTE', 'cambio-lampara.jpg'),
    foto(3, 'DESPUES', 'despues.jpg'),
  ]
}

function crearBaseInicial(): Base {
  const cuadrillas: CuadrillaSimulada[] = [
    { id: 1, nombre: 'Cuadrilla Norte', zona: 'Zona Norte', activa: true },
    { id: 2, nombre: 'Cuadrilla Sur', zona: 'Zona Sur', activa: true },
    { id: 3, nombre: 'Cuadrilla Este', zona: 'Zona Este', activa: true },
    // Sin técnicos, para poder desactivarla.
    { id: 4, nombre: 'Cuadrilla Oeste', zona: 'Zona Oeste', activa: true },
    { id: 5, nombre: 'Cuadrilla Centro', zona: 'Zona Centro', activa: false },
  ]
  const usuarios: UsuarioSimulado[] = [
    usuario(1, 'Jefa de Prueba', 'jefatura@sigma.test', 'JEFATURA', null),
    usuario(2, 'Técnico de Prueba', 'tecnico@sigma.test', 'TECNICO', 1),
    usuario(3, 'Operador de Prueba', 'operador@sigma.test', 'OPERADOR', null),
    usuario(4, 'Encargado de Prueba', 'stock@sigma.test', 'ENCARGADO_STOCK', null),
    usuario(5, 'Usuario Bloqueado', 'bloqueado@sigma.test', 'OPERADOR', null, { bloqueado: true }),
    usuario(6, 'Usuario Desactivado', 'desactivado@sigma.test', 'OPERADOR', null, {
      activo: false,
    }),
  ]
  // Relleno para probar la paginación del listado.
  for (let i = 0; i < 26; i++) {
    const id = usuarios.length + 1
    const nombre = `${NOMBRES[i % NOMBRES.length]} ${APELLIDOS[i % APELLIDOS.length]}`
    const esTecnico = i % 2 === 0
    usuarios.push(
      usuario(
        id,
        nombre,
        `usuario${id}@sigma.test`,
        esTecnico ? 'TECNICO' : 'OPERADOR',
        esTecnico ? (i % 3) + 1 : null,
        { activo: i % 7 !== 0 },
      ),
    )
  }
  return {
    usuarios,
    cuadrillas,
    instalaciones: crearInstalaciones(),
    ordenes: crearOrdenes(),
    incidentes: crearIncidentes(),
    categorias: crearCategorias(),
    fotos: crearFotos(),
    fotosCierre: crearFotosCierre(),
    idSesion: null,
  }
}

function cargar(): Base {
  try {
    const guardada = sessionStorage.getItem(CLAVE_ALMACEN)
    // Con los datos iniciales de base, por si lo guardado es de antes de sumar una colección.
    if (guardada) return { ...crearBaseInicial(), ...(JSON.parse(guardada) as Partial<Base>) }
  } catch {
    // Sin sessionStorage (o con datos corruptos) se arranca de cero.
  }
  return crearBaseInicial()
}

export let base: Base = cargar()

export function guardarBase() {
  try {
    sessionStorage.setItem(CLAVE_ALMACEN, JSON.stringify(base))
  } catch {
    // La simulación sigue funcionando en memoria.
  }
}

/** Vuelve a los datos iniciales (los tests lo llaman entre casos). */
export function reiniciarBaseSimulada() {
  base = crearBaseInicial()
  guardarBase()
}

/** Simula que la sesión del backend venció: el próximo pedido responde 401. */
export function expirarSesionSimulada() {
  base.idSesion = null
  guardarBase()
}
