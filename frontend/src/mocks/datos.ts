import type {
  EstadoIncidente,
  EstadoOT,
  MotivoPausa,
  PausaOT,
  Prioridad,
} from '../features/ot/types'
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
  latitud: number
  longitud: number
}

export type OrdenSimulada = {
  id: number
  numero: string
  estado: EstadoOT
  idCuadrilla: number | null
  inicioSla: string | null
  fechaLimite: string | null
  creadaEn: string
  estadoAnteriorPausa: EstadoOT | null
  pausas: PausaOT[]
  prioridad: Prioridad | null
  idInstalacion: number
  numeroIncidente: string | null
}

export type IncidenteSimulado = {
  id: number
  numero: string
  estado: EstadoIncidente
  prioridad: Prioridad
  idInstalacion: number
  descripcion: string
  tercero: string | null
  causa: string | null
  creadoEn: string
}

type Base = {
  usuarios: UsuarioSimulado[]
  cuadrillas: CuadrillaSimulada[]
  instalaciones: InstalacionSimulada[]
  ordenes: OrdenSimulada[]
  incidentes: IncidenteSimulado[]
  /** Usuario con la sesión abierta, o null. */
  idSesion: number | null
}

const CLAVE_ALMACEN = 'sigma.apiSimulada'

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

/** 25 cruces ficticios alrededor de Montevideo, para probar la paginación del listado. */
function crearInstalaciones(): InstalacionSimulada[] {
  return Array.from({ length: 25 }, (_, i) => {
    const numero = i + 1
    return {
      id: numero,
      codigo: `INST-${String(numero).padStart(3, '0')}`,
      nombre: `Cruce Ficticio ${numero}`,
      rutaZona: RUTAS[i % RUTAS.length],
      latitud: Number((-34.9 + i * 0.012345).toFixed(6)),
      longitud: Number((-56.2 + i * 0.023456).toFixed(6)),
    }
  })
}

const HORA = 3_600_000

function haceHoras(horas: number) {
  return new Date(Date.now() - horas * HORA).toISOString()
}

function pausaAbierta(motivo: MotivoPausa, detalle: string | null, horas: number): PausaOT {
  return {
    motivo,
    detalle,
    inicio: haceHoras(horas),
    fin: null,
    idUsuarioPausa: 3,
    idUsuarioReanudacion: null,
  }
}

/** OTs correctivas ficticias (#39): una asignada a Norte, una en pausa por un tercero y una sin SLA. */
function crearOrdenes(): OrdenSimulada[] {
  return [
    {
      id: 1,
      numero: 'OT-0001',
      estado: 'ASIGNADA',
      idCuadrilla: 1,
      inicioSla: haceHoras(2),
      fechaLimite: new Date(Date.now() + 46 * HORA).toISOString(),
      creadaEn: haceHoras(2),
      estadoAnteriorPausa: null,
      pausas: [],
      prioridad: 'MEDIA',
      idInstalacion: 1,
      numeroIncidente: 'INC-0001',
    },
    {
      id: 2,
      numero: 'OT-0002',
      estado: 'EN_PAUSA',
      idCuadrilla: 1,
      inicioSla: haceHoras(30),
      fechaLimite: haceHoras(6),
      creadaEn: haceHoras(30),
      estadoAnteriorPausa: 'ASIGNADA',
      pausas: [pausaAbierta('TERCERO', 'UTE', 10)],
      prioridad: 'ALTA',
      idInstalacion: 2,
      numeroIncidente: 'INC-0002',
    },
    {
      id: 3,
      numero: 'OT-0003',
      estado: 'PENDIENTE',
      idCuadrilla: null,
      inicioSla: haceHoras(1),
      fechaLimite: null,
      creadaEn: haceHoras(1),
      estadoAnteriorPausa: null,
      pausas: [],
      prioridad: 'MEDIA',
      idInstalacion: 3,
      numeroIncidente: 'INC-0003',
    },
  ]
}

function crearIncidentes(): IncidenteSimulado[] {
  return [
    {
      id: 4,
      numero: 'INC-0004',
      estado: 'EN_ESPERA_TERCERO',
      prioridad: 'MEDIA',
      idInstalacion: 4,
      descripcion: 'Semáforo apagado',
      tercero: 'UTE',
      causa: 'Corte de energía en la zona',
      creadoEn: haceHoras(3),
    },
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
