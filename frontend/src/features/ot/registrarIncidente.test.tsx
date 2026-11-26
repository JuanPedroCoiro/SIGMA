import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { formatearFechaHoraMontevideo } from '../../shared/lib/fechas'
import { mapaSimulado } from '../../test/reactLeafletSimulado'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): INST-001..025, la 25 («Cruce Peñarol») sin regla
// de SLA; categorías de db/datos-prueba (Semáforo apagado → ALTA, Lámpara quemada → MEDIA, Otra
// → MEDIA); OT-0001..0006 e INC-0001..0007, así que lo nuevo es OT-0007 e INC-0008.
const ID_OPERADOR = 3
const ID_JEFATURA = 1
const ID_SEMAFORO_APAGADO = '1'
const ID_LAMPARA_QUEMADA = '2'
const ID_OTRA = '6'
const INST_001 = 'INST-001 — Cruce Ficticio 1'
const DESCRIPCION = 'El semáforo del cruce está apagado.'

async function elegirInstalacion(texto: string, etiqueta: string) {
  fireEvent.change(await screen.findByLabelText('Instalación'), { target: { value: texto } })
  // La búsqueda espera a que se deje de escribir (300 ms): con la suite entera tarda más. Se busca
  // dentro de los resultados porque el mapa tiene un marcador con el mismo nombre (#70).
  const resultados = await screen.findByRole(
    'list',
    { name: 'Instalaciones encontradas' },
    { timeout: 3000 },
  )
  fireEvent.click(await within(resultados).findByRole('button', { name: etiqueta }))
  await screen.findByRole('button', { name: 'Cambiar' })
}

/** El marcador de una instalación en el mapa (react-leaflet simulado, src/test). */
async function marcador(etiqueta: string) {
  const mapa = screen.getByRole('region', { name: 'Mapa de instalaciones' })
  return within(mapa).findByRole('button', { name: etiqueta })
}

async function elegirCategoria(id: string) {
  await screen.findByRole('option', { name: 'Semáforo apagado' })
  fireEvent.change(screen.getByLabelText('Categoría de la falla'), { target: { value: id } })
}

function describir(texto: string) {
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: texto } })
}

function pulsar(nombre: string) {
  fireEvent.click(screen.getByRole('button', { name: nombre }))
}

/** Espera a que se muestre el paso, por su título. */
async function enPaso(titulo: 'Dónde' | 'Qué pasó' | 'Resumen') {
  return screen.findByRole('heading', { level: 2, name: titulo })
}

/** Paso 1 completo, y pasa al 2. */
async function completarDonde(instalacion = 'INST-001', etiqueta = INST_001) {
  await elegirInstalacion(instalacion, etiqueta)
  fireEvent.click(screen.getByLabelText('Detección en el monitoreo'))
  pulsar('Siguiente')
  await enPaso('Qué pasó')
}

/** Paso 2 con categoría y descripción válidas, sin pasar al resumen. */
async function completarQuePaso() {
  await elegirCategoria(ID_SEMAFORO_APAGADO)
  describir(DESCRIPCION)
}

async function irAlResumen() {
  pulsar('Siguiente')
  await enPaso('Resumen')
}

/** Los dos pasos completos: queda en el resumen, sin confirmar. */
async function completar(instalacion?: string, etiqueta?: string) {
  await completarDonde(instalacion, etiqueta)
  await completarQuePaso()
  await irAlResumen()
}

function confirmar() {
  pulsar('Confirmar')
}

/** El valor de una fila del resumen (`<dt>` y `<dd>`). */
function filaDelResumen(etiqueta: string) {
  return screen.getByText(etiqueta, { selector: 'dt' }).nextElementSibling
}

function seRegistro() {
  return base.incidentes.some((i) => i.numero === 'INC-0008')
}

describe('registrar incidente (#40, en pasos #69)', () => {
  beforeEach(() => {
    base.idSesion = ID_OPERADOR
  })

  it('conOt_muestraElNumeroElVencimientoYElLinkALaOt', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    confirmar()

    const estado = await screen.findByRole('status')
    const orden = base.ordenes.find((o) => o.numero === 'OT-0007')!
    expect(estado).toHaveTextContent('Se generó la OT-0007 (incidente INC-0008)')
    expect(estado).toHaveTextContent(
      `vence a las ${formatearFechaHoraMontevideo(orden.fechaLimite!)}`,
    )
    expect(screen.getByRole('link', { name: 'Ver OT-0007' })).toHaveAttribute(
      'href',
      `/ordenes/${orden.id}`,
    )
    expect(orden.idInstalacion).toBe(1)
  })

  it('sinReglaDeSla_muestraSlaSinDefinir', async () => {
    base.idSesion = ID_JEFATURA
    renderizarRuta('/incidentes/nuevo')
    // La búsqueda no distingue mayúsculas ni tildes.
    await completar('PENAROL', 'INST-025 — Cruce Peñarol (sin regla de SLA)')
    confirmar()

    const estado = await screen.findByRole('status')
    expect(estado).toHaveTextContent('Se generó la OT-0007 (incidente INC-0008), SLA sin definir.')
    expect(base.ordenes.find((o) => o.numero === 'OT-0007')!.fechaLimite).toBeNull()
  })

  it('falladeTercero_quedaEnEsperaYApareceEnPendientes', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completarDonde()
    await completarQuePaso()
    fireEvent.click(screen.getByLabelText('La falla es de un tercero'))
    fireEvent.change(screen.getByLabelText('Tercero'), { target: { value: 'UTE' } })
    await irAlResumen()

    expect(filaDelResumen('Falla de un tercero')).toHaveTextContent('Sí: UTE')
    expect(screen.getByText(/queda en espera de tercero, sin OT ni SLA/)).toBeInTheDocument()
    confirmar()

    const estado = await screen.findByRole('status')
    expect(estado).toHaveTextContent(
      'El incidente INC-0008 quedó en espera de tercero; aparece en Pendientes.',
    )
    expect(screen.queryByRole('link', { name: /Ver OT/ })).not.toBeInTheDocument()
    const incidente = base.incidentes.find((i) => i.numero === 'INC-0008')!
    expect(incidente.estado).toBe('EN_ESPERA_TERCERO')
    expect(incidente.causa).toBeNull()

    fireEvent.click(within(estado).getByRole('link', { name: 'Pendientes' }))
    expect(await screen.findByText('INC-0008')).toBeInTheDocument()
  })

  it('categoria_sugiereLaPrioridadYSePuedeCambiar', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completarDonde()
    expect(screen.getByText('Se sugiere al elegir la categoría.')).toBeInTheDocument()

    await elegirCategoria(ID_SEMAFORO_APAGADO)
    const prioridad = screen.getByLabelText(/^Prioridad/)
    expect(prioridad).toHaveValue('ALTA')
    expect(screen.getByText('Sugerida').closest('.campo-ayuda')).toHaveTextContent(
      'Sugerida por la categoría «Semáforo apagado». Podés cambiarla.',
    )

    await elegirCategoria(ID_LAMPARA_QUEMADA)
    expect(prioridad).toHaveValue('MEDIA')

    fireEvent.change(prioridad, { target: { value: 'BAJA' } })
    expect(screen.getByText('Cambiada').closest('.campo-ayuda')).toHaveTextContent(
      'La categoría «Lámpara quemada» sugiere Media.',
    )
    describir(DESCRIPCION)
    await irAlResumen()
    expect(filaDelResumen('Prioridad')).toHaveTextContent(
      'Baja (cambiada; la categoría sugiere Media)',
    )
    confirmar()

    await screen.findByRole('status')
    expect(base.incidentes.find((i) => i.numero === 'INC-0008')!.prioridad).toBe('BAJA')
  })

  it('pasoDonde_sinCompletar_noAvanzaYMarcaLosCampos', async () => {
    renderizarRuta('/incidentes/nuevo')
    await enPaso('Dónde')
    expect(screen.getByText('Paso 1 de 3')).toBeInTheDocument()
    pulsar('Siguiente')

    expect(await screen.findByText('Elegí la instalación.')).toBeInTheDocument()
    expect(screen.getByText('Indicá el origen.')).toBeInTheDocument()
    expect(screen.getByText('Paso 1 de 3')).toBeInTheDocument()
    expect(screen.getByLabelText('Instalación')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Instalación')).toHaveFocus()

    // Después del intento, el error se va al corregir el campo.
    fireEvent.click(screen.getByLabelText('Reclamo de un organismo'))
    await waitFor(() => expect(screen.queryByText('Indicá el origen.')).not.toBeInTheDocument())
    expect(screen.getByText('Elegí la instalación.')).toBeInTheDocument()
  })

  it('pasoQuePaso_sinCompletar_noAvanzaYMarcaLosCampos', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completarDonde()
    expect(screen.getByText('Paso 2 de 3')).toBeInTheDocument()
    await screen.findByRole('option', { name: 'Semáforo apagado' })
    fireEvent.click(screen.getByLabelText('La falla es de un tercero'))
    pulsar('Siguiente')

    expect(await screen.findByText('Elegí la categoría de la falla.')).toBeInTheDocument()
    expect(screen.getByText('Elegí la prioridad.')).toBeInTheDocument()
    expect(screen.getByText('Describí la falla.')).toBeInTheDocument()
    expect(screen.getByText('Indicá el tercero responsable de la falla.')).toBeInTheDocument()
    // La causa es opcional.
    expect(screen.getByLabelText('Causa (opcional)')).not.toHaveAttribute('aria-invalid')
    expect(screen.getByText('Paso 2 de 3')).toBeInTheDocument()
    expect(seRegistro()).toBe(false)
  })

  it('categoriaOtra_pideDetalleEnLaDescripcion', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completarDonde()
    await elegirCategoria(ID_OTRA)
    expect(
      screen.getByText('Con la categoría «Otra», detallá la falla en la descripción.'),
    ).toBeInTheDocument()
    describir('Raro')
    pulsar('Siguiente')

    expect(
      await screen.findByText(/al menos 10 caracteres/, { selector: '.campo-error' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Paso 2 de 3')).toBeInTheDocument()
  })

  it('anterior_noPierdeLoCargado', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completarDonde()
    await completarQuePaso()
    pulsar('Anterior')

    await enPaso('Dónde')
    expect(screen.getByText(INST_001, { selector: 'span' })).toBeInTheDocument()
    expect(screen.getByLabelText('Detección en el monitoreo')).toBeChecked()

    pulsar('Siguiente')
    await enPaso('Qué pasó')
    expect(screen.getByLabelText('Categoría de la falla')).toHaveValue(ID_SEMAFORO_APAGADO)
    expect(screen.getByLabelText('Descripción')).toHaveValue(DESCRIPCION)
  })

  it('atrasDelNavegador_vuelveAlPasoAnteriorConLosDatos', async () => {
    const { router } = renderizarRuta('/incidentes/nuevo')
    await completar()
    expect(router.state.location.search).toBe('?paso=3')

    await act(() => router.navigate(-1))
    await enPaso('Qué pasó')
    expect(screen.getByLabelText('Descripción')).toHaveValue(DESCRIPCION)

    await act(() => router.navigate(-1))
    await enPaso('Dónde')
    expect(screen.getByText(INST_001, { selector: 'span' })).toBeInTheDocument()
  })

  it('pasoSinCompletarLosAnteriores_vuelveAlPrimero', async () => {
    const { router } = renderizarRuta('/incidentes/nuevo?paso=3')

    await enPaso('Dónde')
    expect(screen.getByText('Paso 1 de 3')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })

  it('resumen_muestraLosDatosYSeEditaPorSeccion', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    expect(screen.getByText('Paso 3 de 3')).toBeInTheDocument()
    expect(filaDelResumen('Instalación')).toHaveTextContent(INST_001)
    expect(filaDelResumen('Origen')).toHaveTextContent('Detección en el monitoreo')
    expect(filaDelResumen('Categoría')).toHaveTextContent('Semáforo apagado')
    expect(filaDelResumen('Prioridad')).toHaveTextContent('Alta (sugerida por la categoría)')
    expect(filaDelResumen('Descripción')).toHaveTextContent(DESCRIPCION)
    expect(filaDelResumen('Falla de un tercero')).toHaveTextContent('No')
    expect(screen.getByText(/se genera la OT correctiva/)).toBeInTheDocument()

    pulsar('Editar Qué pasó')
    await enPaso('Qué pasó')
    describir('El semáforo quedó en amarillo intermitente.')
    pulsar('Volver al resumen')
    await enPaso('Resumen')
    expect(filaDelResumen('Descripción')).toHaveTextContent(
      'El semáforo quedó en amarillo intermitente.',
    )

    // Desde el paso 1 también se vuelve directo al resumen, sin pasar por el 2.
    pulsar('Editar Dónde')
    await enPaso('Dónde')
    fireEvent.click(screen.getByLabelText('Reclamo de un organismo'))
    pulsar('Volver al resumen')
    await enPaso('Resumen')
    expect(filaDelResumen('Origen')).toHaveTextContent('Reclamo de un organismo')
    expect(seRegistro()).toBe(false)

    confirmar()
    await screen.findByRole('status')
    const incidente = base.incidentes.find((i) => i.numero === 'INC-0008')!
    expect(incidente.descripcion).toBe('El semáforo quedó en amarillo intermitente.')
  })

  it('errorDelServidorEnUnCampo_llevaAlPasoDelCampo', async () => {
    servidor.use(
      http.post('/api/v1/incidentes', () =>
        problema(400, 'validacion', 'Datos inválidos', 'Revisá los campos.', {
          errores: [{ campo: 'descripcion', mensaje: 'La descripción no es válida.' }],
        }),
      ),
    )
    renderizarRuta('/incidentes/nuevo')
    await completar()
    confirmar()

    await enPaso('Qué pasó')
    expect(screen.getByRole('alert')).toHaveTextContent('Revisá los datos marcados.')
    expect(screen.getByText('La descripción no es válida.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Descripción')).toHaveFocus())

    servidor.use(
      http.post('/api/v1/incidentes', () =>
        problema(400, 'validacion', 'Datos inválidos', 'Revisá los campos.', {
          errores: [{ campo: 'idInstalacion', mensaje: 'La instalación no es válida.' }],
        }),
      ),
    )
    pulsar('Volver al resumen')
    await enPaso('Resumen')
    confirmar()

    await enPaso('Dónde')
    expect(screen.getByText('La instalación no es válida.')).toBeInTheDocument()
  })

  it('errorGeneralDelServidor_seMuestraEnElResumen', async () => {
    servidor.use(
      http.post('/api/v1/incidentes', () =>
        problema(
          400,
          'regla-negocio',
          'Regla de negocio no cumplida',
          'La instalación 1 no existe o está inactiva.',
        ),
      ),
    )
    renderizarRuta('/incidentes/nuevo')
    await completar()
    confirmar()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La instalación 1 no existe o está inactiva.',
    )
    expect(screen.getByText('Paso 3 de 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled()
  })

  it('registrarOtro_vuelveAlPrimerPasoVacio', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    confirmar()
    await screen.findByRole('status')

    pulsar('Registrar otro incidente')

    await enPaso('Dónde')
    expect(screen.getByText('Paso 1 de 3')).toBeInTheDocument()
    expect(screen.getByLabelText('Instalación')).toHaveValue('')
    expect(screen.getByLabelText('Detección en el monitoreo')).not.toBeChecked()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('atrasDespuesDeConfirmar_noVuelveAlResumenEnviado', async () => {
    const { router } = renderizarRuta('/incidentes/nuevo')
    await completar()
    confirmar()
    await screen.findByRole('status')

    await act(() => router.navigate(-1))

    await enPaso('Dónde')
    expect(screen.getByLabelText('Instalación')).toHaveValue('')
    expect(base.incidentes.filter((i) => i.numero === 'INC-0009')).toHaveLength(0)
  })

  describe('mapa al costado (#70)', () => {
    const INST_003 = 'INST-003 — Cruce Ficticio 3'

    it('mapa_muestraLasInstalacionesActivasComoMarcadores', async () => {
      renderizarRuta('/incidentes/nuevo')
      await enPaso('Dónde')

      expect(await marcador(INST_001)).toHaveClass('mapa-marcador')
      const mapa = screen.getByRole('region', { name: 'Mapa de instalaciones' })
      expect(within(mapa).getAllByRole('button')).toHaveLength(base.instalaciones.length)
      expect(mapaSimulado.invalidateSize).toHaveBeenCalled()
      await waitFor(() => expect(mapaSimulado.fitBounds).toHaveBeenCalled())
    })

    it('clicEnMarcador_eligeLaInstalacion', async () => {
      renderizarRuta('/incidentes/nuevo')
      await enPaso('Dónde')
      fireEvent.click(await marcador(INST_003))

      expect(await screen.findByRole('button', { name: 'Cambiar' })).toBeInTheDocument()
      expect(screen.getByText(INST_003, { selector: 'span' })).toBeInTheDocument()
      expect(await marcador(INST_003)).toHaveClass('mapa-marcador-elegido')
      fireEvent.click(screen.getByLabelText('Detección en el monitoreo'))
      pulsar('Siguiente')
      await enPaso('Qué pasó')
      await completarQuePaso()
      await irAlResumen()
      expect(filaDelResumen('Instalación')).toHaveTextContent(INST_003)
    })

    it('clicEnMarcador_despuesDeUnIntento_borraElError', async () => {
      renderizarRuta('/incidentes/nuevo')
      await enPaso('Dónde')
      pulsar('Siguiente')
      expect(await screen.findByText('Elegí la instalación.')).toBeInTheDocument()

      fireEvent.click(await marcador(INST_003))
      await waitFor(() =>
        expect(screen.queryByText('Elegí la instalación.')).not.toBeInTheDocument(),
      )
    })

    it('elegirEnElBuscador_centraElMapaYResaltaElMarcador', async () => {
      renderizarRuta('/incidentes/nuevo')
      await enPaso('Dónde')
      await elegirInstalacion('INST-003', INST_003)

      expect(await marcador(INST_003)).toHaveClass('mapa-marcador-elegido')
      expect(await marcador(INST_001)).not.toHaveClass('mapa-marcador-elegido')
      const inst003 = base.instalaciones.find((i) => i.codigo === 'INST-003')!
      await waitFor(() =>
        expect(mapaSimulado.setView).toHaveBeenCalledWith(
          [inst003.latitud, inst003.longitud],
          expect.any(Number),
        ),
      )

      // Al cambiarla, ya no queda resaltada.
      pulsar('Cambiar')
      await waitFor(async () =>
        expect(await marcador(INST_003)).not.toHaveClass('mapa-marcador-elegido'),
      )
    })

    it('errorAlCargarElMapa_sePuedeBuscarIgual', async () => {
      servidor.use(
        http.get('/api/v1/instalaciones/mapa', () => HttpResponse.json(null, { status: 500 })),
      )
      renderizarRuta('/incidentes/nuevo')
      await enPaso('Dónde')

      expect(
        await screen.findByText(
          'No se pudo cargar el mapa. Podés buscar la instalación por código o nombre.',
        ),
      ).toBeInTheDocument()
      await elegirInstalacion('INST-001', INST_001)
    })
  })

  describe('fotos del incidente (#71)', () => {
    const PNG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0]
    const JPG = [0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0]

    function foto(nombre: string, tipo = 'image/png', bytes = PNG) {
      return new File([new Uint8Array(bytes)], nombre, { type: tipo })
    }

    function elegirFotos(...archivos: File[]) {
      fireEvent.change(screen.getByLabelText('Fotos (opcional)'), {
        target: { files: archivos },
      })
    }

    function miniaturas() {
      const lista = screen.queryByRole('list', { name: 'Fotos elegidas' })
      return lista
        ? within(lista)
            .getAllByRole('img')
            .map((img) => img.getAttribute('alt'))
        : []
    }

    it('fotos_seVenEnElResumenYSeSubenAlConfirmar', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      await completarQuePaso()
      elegirFotos(foto('reclamo.png'), foto('cruce.jpg', 'image/jpeg', JPG))
      expect(miniaturas()).toEqual(['reclamo.png', 'cruce.jpg'])

      await irAlResumen()
      expect(miniaturas()).toEqual(['reclamo.png', 'cruce.jpg'])
      confirmar()

      expect(await screen.findByRole('status')).toHaveTextContent(
        'Se generó la OT-0007 (incidente INC-0008)',
      )
      expect(screen.getByRole('status')).toHaveTextContent('Se adjuntaron 2 fotos.')
      const incidente = base.incidentes.find((i) => i.numero === 'INC-0008')!
      expect(base.fotos.filter((f) => f.idIncidente === incidente.id)).toMatchObject([
        { nombreOriginal: 'reclamo.png', tipo: 'image/png' },
        { nombreOriginal: 'cruce.jpg', tipo: 'image/jpeg' },
      ])
    })

    it('sinFotos_elResumenLoDiceYNoSeSubeNada', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completar()
      expect(filaDelResumen('Fotos')).toHaveTextContent('Sin fotos')
      confirmar()

      expect(await screen.findByRole('status')).not.toHaveTextContent('Se adjuntaron')
      expect(base.fotos).toHaveLength(0)
    })

    it('quitar_sacaLaFotoYLiberaLaVistaPrevia', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      elegirFotos(foto('una.png'), foto('otra.png'))

      pulsar('Quitar una.png')

      expect(miniaturas()).toEqual(['otra.png'])
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
    })

    it('tipoTamanioYMaximo_seRechazanAlElegir', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      const grande = foto('grande.png')
      Object.defineProperty(grande, 'size', { value: 10 * 1024 * 1024 + 1 })

      elegirFotos(
        foto('informe.pdf', 'application/pdf'),
        grande,
        foto('1.png'),
        foto('2.png'),
        foto('3.png'),
        foto('4.png'),
      )

      const errores = screen.getByRole('alert')
      expect(errores).toHaveTextContent('«informe.pdf» no es una imagen JPG, PNG ni WEBP.')
      expect(errores).toHaveTextContent('«grande.png» supera los 10 MB.')
      expect(errores).toHaveTextContent('Se pueden adjuntar hasta 3 fotos: quedó afuera 1.')
      expect(miniaturas()).toEqual(['1.png', '2.png', '3.png'])
      expect(screen.getByLabelText('Fotos (opcional)')).toBeDisabled()
    })

    it('volverAlPaso_conservaLasFotos', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      await completarQuePaso()
      elegirFotos(foto('reclamo.png'))
      await irAlResumen()

      pulsar('Editar Fotos')
      await enPaso('Qué pasó')

      expect(miniaturas()).toEqual(['reclamo.png'])
    })

    it('rechazoDelServidor_elIncidenteQuedaYSeAvisa', async () => {
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      await completarQuePaso()
      // Dice ser PNG, pero el contenido no es una imagen: lo detecta el servidor por el contenido.
      elegirFotos(foto('falsa.png', 'image/png', [0x68, 0x6f, 0x6c, 0x61]))
      await irAlResumen()
      confirmar()

      expect(await screen.findByRole('status')).toHaveTextContent('Se generó la OT-0007')
      expect(screen.getByRole('alert')).toHaveTextContent(
        'El incidente quedó registrado, pero no se pudieron subir las fotos. «falsa.png» no es una imagen JPG, PNG ni WEBP.',
      )
      expect(seRegistro()).toBe(true)
      expect(base.fotos).toHaveLength(0)
    })

    it('falloAlSubir_sePuedeReintentar', async () => {
      servidor.use(
        http.post(
          '/api/v1/incidentes/:id/fotos',
          () => problema(503, 'no-encontrado', 'No disponible', 'El almacenamiento no responde.'),
          { once: true },
        ),
      )
      renderizarRuta('/incidentes/nuevo')
      await completarDonde()
      await completarQuePaso()
      elegirFotos(foto('reclamo.png'))
      await irAlResumen()
      confirmar()

      expect(await screen.findByRole('alert')).toHaveTextContent('El almacenamiento no responde.')
      expect(seRegistro()).toBe(true)

      pulsar('Reintentar subir las fotos')

      await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
      expect(screen.getByRole('status')).toHaveTextContent('Se adjuntaron 1 foto.')
      expect(base.fotos).toHaveLength(1)
    })

    it('detalleDeLaOt_muestraLasFotosDelIncidente', async () => {
      base.idSesion = ID_JEFATURA
      const orden = base.ordenes.find((o) => o.numero === 'OT-0001')!
      base.fotos.push({
        id: 1,
        idIncidente: orden.idIncidente!,
        nombreOriginal: 'reclamo.jpg',
        tipo: 'image/jpeg',
        tamanioBytes: 1234,
        subidaEn: new Date().toISOString(),
      })

      renderizarRuta(`/ordenes/${orden.id}`)

      const lista = await screen.findByRole('list', { name: 'Fotos del incidente' })
      expect(within(lista).getByRole('img', { name: 'reclamo.jpg' })).toHaveAttribute(
        'src',
        `/api/v1/incidentes/${orden.idIncidente}/fotos/1`,
      )
    })
  })
})
