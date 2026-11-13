import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): OT-0001 asignada a Norte para hoy (vence en 46 h),
// OT-0002 en pausa por UTE (Norte, hoy), OT-0003 pendiente sin SLA, INC-0004 en espera de UTE.
const ID_JEFATURA = 1
const ID_TECNICO = 2
const ID_OPERADOR = 3
const ID_ENCARGADO = 4
const HORA = 3_600_000

async function resumen() {
  return screen.findByRole('list', { name: 'Resumen' })
}

describe('inicio con resumen por rol (#72)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('jefatura_tarjetasConSusEnlaces', async () => {
    renderizarRuta('/')
    const lista = await resumen()

    expect(within(lista).getByRole('link', { name: '1 OT pendiente de asignar' })).toHaveAttribute(
      'href',
      '/pendientes?estado=PENDIENTE',
    )
    expect(within(lista).getByRole('link', { name: '1 OT en pausa' })).toHaveAttribute(
      'href',
      '/pendientes?estado=EN_PAUSA',
    )
    // OT-0002 tiene la fecha límite pasada, pero está en pausa por un tercero: no vence.
    expect(within(lista).getByRole('link', { name: '0 OTs vencidas' })).toBeInTheDocument()
    expect(
      within(lista).getByRole('link', { name: '1 incidente en espera de tercero' }),
    ).toBeInTheDocument()
    expect(lista).not.toHaveTextContent('registrados hoy')
  })

  it('pendientesDeAsignar_llevaAlTableroFiltrado', async () => {
    const { router } = renderizarRuta('/')
    const lista = await resumen()

    fireEvent.click(within(lista).getByRole('link', { name: '1 OT pendiente de asignar' }))

    expect(await screen.findByRole('link', { name: 'OT-0003' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?estado=PENDIENTE')
    expect(screen.queryByRole('link', { name: 'OT-0001' })).not.toBeInTheDocument()
  })

  it('operador_vencidasEnRojoEIncidentesDeHoy', async () => {
    base.idSesion = ID_OPERADOR
    base.ordenes.find((o) => o.id === 1)!.fechaLimite = new Date(Date.now() - HORA).toISOString()
    // Tres de hoy y uno de hace 30 h.
    base.incidentes.forEach((incidente, i) => {
      incidente.creadoEn = new Date(Date.now() - (i === 1 ? 30 * HORA : 0)).toISOString()
    })
    renderizarRuta('/')
    const lista = await resumen()

    expect(lista).toHaveTextContent('3 incidentes registrados hoy')
    expect(within(lista).getByRole('link', { name: '1 OT vencida' }).closest('li')).toHaveClass(
      'tarjeta-alerta',
    )
    expect(lista).not.toHaveTextContent('pendiente de asignar')
    const inicio = screen.getByRole('heading', { name: /^Hola/ }).closest('section')!
    expect(within(inicio).getByRole('link', { name: 'Registrar incidente' })).toBeInTheDocument()
  })

  it('tecnico_lasDeSuCuadrillaParaHoySinRegistrar', async () => {
    base.idSesion = ID_TECNICO
    renderizarRuta('/')
    const lista = await resumen()

    expect(lista).toHaveTextContent('2 OTs de tu cuadrilla para hoy')
    const inicio = screen.getByRole('heading', { name: /^Hola/ }).closest('section')!
    expect(
      within(inicio).queryByRole('link', { name: 'Registrar incidente' }),
    ).not.toBeInTheDocument()
  })

  it('encargadoDeStock_sinTarjetas', async () => {
    base.idSesion = ID_ENCARGADO
    renderizarRuta('/')

    expect(await screen.findByText('Elegí una opción del menú para empezar.')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('list', { name: 'Resumen' })).not.toBeInTheDocument(),
    )
  })
})
