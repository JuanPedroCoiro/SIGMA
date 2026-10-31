import { http, HttpResponse } from 'msw/http'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { base, guardarBase, type InstalacionSimulada } from '../datos'
import { problema, sinPermiso, sinSesion, usuarioEnSesion } from '../problemas'

// Endpoints de instalaciones acordados con el backend (HU-02): el listado es para JEFATURA y
// OPERADOR; la importación, solo para JEFATURA.

const TAMANIO_POR_DEFECTO = 20
const TAMANIO_MAXIMO = 100
const COLUMNAS = ['codigo', 'nombre', 'ruta_zona', 'latitud', 'longitud'] as const

type ErrorFila = { fila: number; campo: string; mensaje: string }

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

export const handlersInstalaciones = [
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
    const ordenadas = [...base.instalaciones].sort((a, b) => a.codigo.localeCompare(b.codigo))
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
