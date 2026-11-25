import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base, type OrdenSimulada } from '../../mocks/datos'
import { formatearFechaMontevideo, hoyEnMontevideo } from '../../shared/lib/fechas'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): el Técnico de Prueba es de Cuadrilla Norte.
// - OT-0001: asignada a Norte para hoy, INST-001, de INC-0001 («Lámpara quemada», media), vence en 46 h.
// - OT-0002: en pausa por UTE, de Norte para hoy, INST-002, alta; su fecha límite ya pasó.
// - OT-0003: pendiente sin cuadrilla.
const ID_TECNICO_NORTE = 2
const HORA = 3_600_000

function orden(id: number) {
  return base.ordenes.find((o) => o.id === id)!
}

/** Una OT más de Norte, asignada para hoy, sobre la instalación `id`. */
function ordenDeNorte(id: number, extra: Partial<OrdenSimulada> = {}): OrdenSimulada {
  return {
    ...orden(1),
    id,
    numero: `OT-${String(id).padStart(4, '0')}`,
    idInstalacion: (id % 20) + 1,
    pausas: [],
    creadaEn: new Date(Date.now() - id * 1000).toISOString(),
    ...extra,
  }
}

function tarjeta(numero: string) {
  return within(screen.getByRole('listitem', { name: numero }))
}

async function bandeja() {
  return screen.findByRole('list', { name: 'OTs de tu cuadrilla' })
}

describe('bandeja «Mis OTs» (HU-07)', () => {
  beforeEach(() => {
    base.idSesion = ID_TECNICO_NORTE
  })

  it('muestraLasDeSuCuadrillaConLoQueHaceFaltaParaIr', async () => {
    renderizarRuta('/mis-ots')

    const lista = await bandeja()
    expect(within(lista).getAllByRole('listitem')).toHaveLength(2)
    expect(within(lista).queryByText('OT-0003')).not.toBeInTheDocument()

    const primera = tarjeta('OT-0001')
    expect(primera.getByRole('link', { name: 'OT-0001' })).toHaveAttribute('href', '/mis-ots/1')
    expect(primera.getByText('Asignada')).toBeVisible()
    expect(primera.getByText('Prioridad media')).toBeVisible()
    expect(primera.getByText('Lámpara quemada')).toBeVisible()
    expect(primera.getByText('INST-001 — Cruce Ficticio 1')).toBeVisible()
    expect(primera.getByText('Calle Ficticia 100 esq. Avenida de Prueba')).toBeVisible()
    expect(primera.getByText('Ruta 1')).toBeVisible()
    expect(primera.getByText(formatearFechaMontevideo(hoyEnMontevideo()))).toBeVisible()
    expect(primera.queryByText('Vencida')).not.toBeInTheDocument()
    expect(primera.getByRole('link', { name: 'Cómo llegar a INST-001' })).toHaveAttribute(
      'href',
      'https://www.google.com/maps/dir/?api=1&destination=-34.9,-56.2',
    )
    expect(primera.getByRole('button', { name: 'Iniciar intervención de OT-0001' })).toBeVisible()

    // En pausa por un tercero: se ve la pausa y no vence aunque pasó la fecha límite.
    const segunda = tarjeta('OT-0002')
    expect(segunda.getByText('En pausa: Falla de un tercero')).toBeVisible()
    expect(segunda.getByText(/\(UTE\)/)).toBeVisible()
    expect(segunda.queryByText('Vencida')).not.toBeInTheDocument()
    expect(segunda.queryByRole('button', { name: /Iniciar/ })).not.toBeInTheDocument()
  })

  it('vencida_seResalta', async () => {
    orden(1).fechaLimite = new Date(Date.now() - HORA).toISOString()
    renderizarRuta('/mis-ots')
    await bandeja()

    expect(tarjeta('OT-0001').getByText('Vencida')).toBeVisible()
    expect(screen.getByRole('listitem', { name: 'OT-0001' })).toHaveClass('tarjeta-mi-ot-vencida')
  })

  it('porFechaDeEjecucion_laAtrasadaPrimero', async () => {
    orden(2).fechaEjecucion = '2020-01-01'
    renderizarRuta('/mis-ots')

    const lista = await bandeja()
    const numeros = within(lista)
      .getAllByRole('heading')
      .map((h) => h.textContent)
    expect(numeros).toEqual(['OT-0002', 'OT-0001'])
  })

  it('anuladaOReasignada_saleDeLaBandeja', async () => {
    // Pendiente de #47: una OT Asignada que se anula, o que se reasigna, deja de figurar.
    orden(1).estado = 'ANULADA'
    orden(2).idCuadrilla = 2
    renderizarRuta('/mis-ots')

    expect(await screen.findByText(/No tenés OTs asignadas/)).toBeVisible()
  })

  it('tecnicoSinCuadrilla_mensajeSinOts', async () => {
    base.usuarios.find((u) => u.id === ID_TECNICO_NORTE)!.idCuadrilla = null
    renderizarRuta('/mis-ots')

    expect(await screen.findByText(/No tenés OTs asignadas/)).toBeVisible()
  })

  it('masDeVeinte_paginada', async () => {
    for (let id = 4; id <= 24; id++) base.ordenes.push(ordenDeNorte(id))
    renderizarRuta('/mis-ots')

    expect(await screen.findByText('Página 1 de 2 (23 en total)')).toBeVisible()
    expect(within(await bandeja()).getAllByRole('listitem')).toHaveLength(20)

    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Página 2 de 2 (23 en total)')).toBeVisible()
    expect(within(await bandeja()).getAllByRole('listitem')).toHaveLength(3)
  })

  it('contadorDelInicio_llevaALaBandeja', async () => {
    renderizarRuta('/')

    fireEvent.click(await screen.findByRole('link', { name: '2 OTs de tu cuadrilla para hoy' }))

    expect(await screen.findByRole('heading', { name: 'Mis OTs' })).toBeVisible()
    expect(await bandeja()).toBeVisible()
  })
})
