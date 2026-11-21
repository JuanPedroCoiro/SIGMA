import { http, HttpResponse } from 'msw/http'
import { MAXIMO_FOTOS, TAMANIO_MAXIMO_FOTO } from '../../features/ot/fotos'
import type { FotoIncidente } from '../../features/ot/types'
import { base, guardarBase, type FotoSimulada } from '../datos'
import { problema } from '../problemas'
import { rechazoJefaturaUOperador } from './pendientes'

// Fotos del incidente (#71), como FotosIncidenteController: Operador y Jefatura. El tipo se
// reconoce por el contenido. Los datos van en la base simulada; el contenido, solo en memoria
// (no entra en sessionStorage): después de recargar, la imagen es un marcador.

const contenidos = new Map<number, { bytes: Uint8Array<ArrayBuffer>; tipo: string }>()

const MARCADOR = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
<rect width="160" height="160" fill="#e5e7eb"/>
<text x="80" y="85" font-family="sans-serif" font-size="14" text-anchor="middle" fill="#6b7280">Foto simulada</text>
</svg>`

/** El tipo por los primeros bytes (TipoImagen.detectar en el backend), o null. */
function detectarTipo(bytes: Uint8Array): string | null {
  const empieza = (...valores: number[]) => valores.every((v, i) => bytes[i] === v)
  if (empieza(0xff, 0xd8, 0xff)) return 'image/jpeg'
  if (empieza(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return 'image/png'
  const texto = (desde: number, hasta: number) =>
    String.fromCharCode(...bytes.subarray(desde, hasta))
  if (bytes.length >= 12 && texto(0, 4) === 'RIFF' && texto(8, 12) === 'WEBP') return 'image/webp'
  return null
}

const fotoInvalida = (detalle: string) =>
  problema(400, 'foto-invalida', 'Regla de negocio no cumplida', detalle)

const noEncontrado = (detalle: string) =>
  problema(404, 'no-encontrado', 'Recurso no encontrado', detalle)

function respuesta(foto: FotoSimulada): FotoIncidente {
  return {
    id: foto.id,
    nombreOriginal: foto.nombreOriginal,
    tipo: foto.tipo,
    tamanioBytes: foto.tamanioBytes,
    subidaEn: foto.subidaEn,
    url: `/api/v1/incidentes/${foto.idIncidente}/fotos/${foto.id}`,
  }
}

type Parte = { nombre: string; bytes: Uint8Array<ArrayBuffer> }

/**
 * Los archivos del campo `campo` del multipart. Se lee el cuerpo a mano, byte a byte, porque en
 * los tests `request.formData()` de Node rechaza el File de jsdom (como en instalaciones.ts).
 */
async function archivosDelMultipart(request: Request, campo: string): Promise<Parte[]> {
  const limite = /boundary=(.+)$/.exec(request.headers.get('Content-Type') ?? '')?.[1]
  if (!limite) return []
  const bytes = new Uint8Array(await request.arrayBuffer())
  // Un carácter por byte, para poder buscar los separadores sin alterar el contenido binario.
  let texto = ''
  for (let i = 0; i < bytes.length; i += 8192) {
    texto += String.fromCharCode(...bytes.subarray(i, i + 8192))
  }
  const partes: Parte[] = []
  let desde = texto.indexOf(`--${limite}`)
  while (desde !== -1) {
    // Después de «--límite» viene un salto de línea y los encabezados de la parte.
    const inicio = desde + limite.length + 4
    const hasta = texto.indexOf(`\r\n--${limite}`, inicio)
    if (hasta === -1) break
    const parte = texto.slice(inicio, hasta)
    const finEncabezado = parte.indexOf('\r\n\r\n')
    const encabezado = new TextDecoder().decode(
      bytes.subarray(inicio, inicio + Math.max(finEncabezado, 0)),
    )
    const nombre = /filename="([^"]*)"/.exec(encabezado)?.[1]
    if (finEncabezado !== -1 && nombre !== undefined && encabezado.includes(`name="${campo}"`)) {
      partes.push({ nombre, bytes: bytes.slice(inicio + finEncabezado + 4, hasta) })
    }
    desde = hasta + 2
  }
  return partes
}

function fotosDe(idIncidente: number) {
  return base.fotos.filter((f) => f.idIncidente === idIncidente)
}

export const handlersFotos = [
  http.post('/api/v1/incidentes/:id/fotos', async ({ params, request }) => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const idIncidente = Number(params.id)
    if (!base.incidentes.some((i) => i.id === idIncidente)) {
      return noEncontrado(`No existe el incidente ${idIncidente}.`)
    }
    const archivos = await archivosDelMultipart(request, 'fotos')
    if (archivos.length === 0) return fotoInvalida('Elegí al menos una foto.')
    const yaTiene = fotosDe(idIncidente).length
    if (yaTiene + archivos.length > MAXIMO_FOTOS) {
      return problema(
        400,
        'demasiadas-fotos',
        'Regla de negocio no cumplida',
        `Un incidente admite hasta ${MAXIMO_FOTOS} fotos y ya tiene ${yaTiene}. Quitá alguna de las nuevas.`,
      )
    }

    // Se validan todas antes de guardar ninguna, como el backend.
    const validadas: (Parte & { tipo: string })[] = []
    for (const archivo of archivos) {
      const nombre = archivo.nombre ? `«${archivo.nombre}»` : 'La foto'
      const tamanio = archivo.bytes.byteLength
      if (tamanio === 0) return fotoInvalida(`${nombre} está vacía.`)
      if (tamanio > TAMANIO_MAXIMO_FOTO) return fotoInvalida(`${nombre} supera los 10 MB.`)
      const tipo = detectarTipo(archivo.bytes)
      if (!tipo) return fotoInvalida(`${nombre} no es una imagen JPG, PNG ni WEBP.`)
      validadas.push({ ...archivo, tipo })
    }

    const subidas = validadas.map(({ nombre, bytes, tipo }) => {
      const foto: FotoSimulada = {
        id: Math.max(0, ...base.fotos.map((f) => f.id)) + 1,
        idIncidente,
        nombreOriginal: nombre,
        tipo,
        tamanioBytes: bytes.byteLength,
        subidaEn: new Date().toISOString(),
      }
      base.fotos.push(foto)
      contenidos.set(foto.id, { bytes, tipo })
      return foto
    })
    guardarBase()
    return HttpResponse.json(subidas.map(respuesta), { status: 201 })
  }),

  http.get('/api/v1/incidentes/:id/fotos', ({ params }) => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const idIncidente = Number(params.id)
    if (!base.incidentes.some((i) => i.id === idIncidente)) {
      return noEncontrado(`No existe el incidente ${idIncidente}.`)
    }
    return HttpResponse.json(fotosDe(idIncidente).map(respuesta))
  }),

  http.get('/api/v1/incidentes/:id/fotos/:idFoto', ({ params }) => {
    const rechazo = rechazoJefaturaUOperador()
    if (rechazo) return rechazo
    const idFoto = Number(params.idFoto)
    const foto = base.fotos.find((f) => f.id === idFoto && f.idIncidente === Number(params.id))
    if (!foto) return noEncontrado(`No existe la foto ${idFoto}.`)
    const contenido = contenidos.get(foto.id)
    if (!contenido) {
      return new HttpResponse(MARCADOR, { headers: { 'Content-Type': 'image/svg+xml' } })
    }
    return new HttpResponse(contenido.bytes, { headers: { 'Content-Type': contenido.tipo } })
  }),
]
