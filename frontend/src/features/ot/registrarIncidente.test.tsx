import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { formatearFechaHoraMontevideo } from '../../shared/lib/fechas'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): INST-001..025, la 25 («Cruce Peñarol») sin regla
// de SLA; categorías de db/datos-prueba (Semáforo apagado → ALTA, Lámpara quemada → MEDIA, Otra
// → MEDIA); OT-0001..0003 e INC-0004, así que lo nuevo es OT-0004 e INC-0005.
const ID_OPERADOR = 3
const ID_JEFATURA = 1
const ID_SEMAFORO_APAGADO = '1'
const ID_LAMPARA_QUEMADA = '2'
const ID_OTRA = '6'

async function elegirInstalacion(texto: string, etiqueta: string) {
  fireEvent.change(await screen.findByLabelText('Instalación'), { target: { value: texto } })
  // La búsqueda espera a que se deje de escribir (300 ms): con la suite entera tarda más.
  fireEvent.click(await screen.findByRole('button', { name: etiqueta }, { timeout: 3000 }))
  await screen.findByRole('button', { name: 'Cambiar' })
}

async function elegirCategoria(id: string) {
  await screen.findByRole('option', { name: 'Semáforo apagado' })
  fireEvent.change(screen.getByLabelText('Categoría de la falla'), { target: { value: id } })
}

function describir(texto: string) {
  fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: texto } })
}

function enviar() {
  fireEvent.click(screen.getByRole('button', { name: 'Registrar incidente' }))
}

/** Instalación, origen, categoría y descripción válidos. */
async function completar(instalacion = 'INST-001', etiqueta = 'INST-001 — Cruce Ficticio 1') {
  await elegirInstalacion(instalacion, etiqueta)
  fireEvent.click(screen.getByLabelText('Detección en el monitoreo'))
  await elegirCategoria(ID_SEMAFORO_APAGADO)
  describir('El semáforo del cruce está apagado.')
}

describe('registrar incidente (#40)', () => {
  beforeEach(() => {
    base.idSesion = ID_OPERADOR
  })

  it('conOt_muestraElNumeroElVencimientoYElLinkALaOt', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    enviar()

    const estado = await screen.findByRole('status')
    const orden = base.ordenes.find((o) => o.numero === 'OT-0004')!
    expect(estado).toHaveTextContent('Se generó la OT-0004 (incidente INC-0005)')
    expect(estado).toHaveTextContent(
      `vence a las ${formatearFechaHoraMontevideo(orden.fechaLimite!)}`,
    )
    expect(screen.getByRole('link', { name: 'Ver OT-0004' })).toHaveAttribute(
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
    enviar()

    const estado = await screen.findByRole('status')
    expect(estado).toHaveTextContent('Se generó la OT-0004 (incidente INC-0005), SLA sin definir.')
    expect(base.ordenes.find((o) => o.numero === 'OT-0004')!.fechaLimite).toBeNull()
  })

  it('falladeTercero_quedaEnEsperaYApareceEnPendientes', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    fireEvent.click(screen.getByLabelText('La falla es de un tercero'))
    fireEvent.change(screen.getByLabelText('Tercero'), { target: { value: 'UTE' } })
    enviar()

    const estado = await screen.findByRole('status')
    expect(estado).toHaveTextContent(
      'El incidente INC-0005 quedó en espera de tercero; aparece en Pendientes.',
    )
    expect(screen.queryByRole('link', { name: /Ver OT/ })).not.toBeInTheDocument()
    const incidente = base.incidentes.find((i) => i.numero === 'INC-0005')!
    expect(incidente.estado).toBe('EN_ESPERA_TERCERO')
    expect(incidente.causa).toBeNull()

    fireEvent.click(within(estado).getByRole('link', { name: 'Pendientes' }))
    expect(await screen.findByText('INC-0005')).toBeInTheDocument()
  })

  it('categoria_proponeLaPrioridadYSePuedeCambiar', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    const prioridad = screen.getByLabelText('Prioridad')
    expect(prioridad).toHaveValue('ALTA')
    expect(screen.getByText(/Propuesta por la categoría: Alta/)).toBeInTheDocument()

    await elegirCategoria(ID_LAMPARA_QUEMADA)
    expect(prioridad).toHaveValue('MEDIA')

    fireEvent.change(prioridad, { target: { value: 'BAJA' } })
    enviar()

    await screen.findByRole('status')
    expect(base.incidentes.find((i) => i.numero === 'INC-0005')!.prioridad).toBe('BAJA')
  })

  it('obligatorios_muestranElMensajeEnCadaCampo', async () => {
    renderizarRuta('/incidentes/nuevo')
    await screen.findByRole('option', { name: 'Semáforo apagado' })
    fireEvent.click(screen.getByLabelText('La falla es de un tercero'))
    enviar()

    expect(await screen.findByText('Elegí la instalación.')).toBeInTheDocument()
    expect(screen.getByText('Indicá el origen.')).toBeInTheDocument()
    expect(screen.getByText('Elegí la categoría de la falla.')).toBeInTheDocument()
    expect(screen.getByText('Describí la falla.')).toBeInTheDocument()
    expect(screen.getByText('Indicá el tercero responsable de la falla.')).toBeInTheDocument()
    // La causa es opcional.
    expect(screen.getByLabelText('Causa (opcional)')).not.toHaveAttribute('aria-invalid')
    expect(base.incidentes.some((i) => i.numero === 'INC-0005')).toBe(false)
  })

  it('categoriaOtra_pideDetalleEnLaDescripcion', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    await elegirCategoria(ID_OTRA)
    expect(
      screen.getByText('Con la categoría «Otra», detallá la falla en la descripción.'),
    ).toBeInTheDocument()
    describir('Raro')
    enviar()

    expect(
      await screen.findByText(/al menos 10 caracteres/, { selector: '.campo-error' }),
    ).toBeInTheDocument()
  })

  it('errorDelServidor_seMuestraEnElCampoOEnLaAlertaGeneral', async () => {
    servidor.use(
      http.post('/api/v1/incidentes', () =>
        problema(400, 'validacion', 'Datos inválidos', 'Revisá los campos.', {
          errores: [{ campo: 'descripcion', mensaje: 'La descripción no es válida.' }],
        }),
      ),
    )
    renderizarRuta('/incidentes/nuevo')
    await completar()
    enviar()
    expect(await screen.findByText('La descripción no es válida.')).toBeInTheDocument()

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
    enviar()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'La instalación 1 no existe o está inactiva.',
    )
  })

  it('registrarOtro_vuelveAlFormularioVacio', async () => {
    renderizarRuta('/incidentes/nuevo')
    await completar()
    enviar()
    await screen.findByRole('status')

    fireEvent.click(screen.getByRole('button', { name: 'Registrar otro incidente' }))

    expect(await screen.findByLabelText('Instalación')).toHaveValue('')
    expect(screen.getByLabelText('Descripción')).toHaveValue('')
    expect(screen.getByLabelText('Categoría de la falla')).toHaveValue('')
    await waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument())
  })
})
