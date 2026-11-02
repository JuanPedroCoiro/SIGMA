import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): OT-0001 MEDIA asignada, vence en 46 h; OT-0002
// ALTA en pausa por UTE (su fecha límite ya pasó); OT-0003 MEDIA sin SLA; INC-0004 en espera de
// UTE.
const ID_JEFATURA = 1
const ID_TECNICO = 2
const ID_OPERADOR = 3

/** Números de OT del tablero, en el orden en que se muestran. */
function numerosDelTablero() {
  const [tablero] = screen.getAllByRole('table')
  return within(tablero)
    .getAllByRole('link')
    .map((link) => link.textContent)
}

describe('tablero de pendientes (#43)', () => {
  beforeEach(() => {
    base.idSesion = ID_OPERADOR
  })

  it('porDefecto_ordenaPorVencimiento', async () => {
    renderizarRuta('/pendientes')
    await screen.findByRole('link', { name: 'OT-0001' })

    expect(numerosDelTablero()).toEqual(['OT-0001', 'OT-0002', 'OT-0003'])
  })

  it('porPrioridad_primeroLaAlta', async () => {
    renderizarRuta('/pendientes')
    await screen.findByRole('link', { name: 'OT-0001' })

    fireEvent.change(screen.getByLabelText('Ordenar por'), { target: { value: 'PRIORIDAD' } })

    await waitFor(() => expect(numerosDelTablero()).toEqual(['OT-0002', 'OT-0001', 'OT-0003']))
  })

  it('marcaVencidasDetenidasPorTerceroYSinSla', async () => {
    base.ordenes.find((o) => o.id === 1)!.fechaLimite = new Date(
      Date.now() - 3_600_000,
    ).toISOString()
    renderizarRuta('/pendientes')

    const fila = (await screen.findByRole('link', { name: 'OT-0001' })).closest('tr')!
    expect(fila).toHaveClass('fila-vencida')
    expect(within(fila).getByText('Vencida')).toBeVisible()
    // OT-0002: su fecha límite pasó, pero en pausa por un tercero no vence.
    expect(screen.getByText('Detenido por un tercero')).toBeVisible()
    expect(screen.getByText('Falla de un tercero: UTE')).toBeVisible()
    expect(screen.getByText('SLA sin definir')).toBeVisible()
  })

  it('generarOt_quitaElIncidenteYSumaLaOtAlTablero', async () => {
    renderizarRuta('/pendientes')
    const fila = (await screen.findByText('INC-0004')).closest('tr')!

    fireEvent.click(within(fila).getByRole('button', { name: 'Generar OT' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Generar OT' })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Generar OT' }))

    expect(await screen.findByText('No hay incidentes en espera de tercero.')).toBeVisible()
    expect(await screen.findByRole('link', { name: 'OT-0004' })).toBeVisible()
  })

  it('cerrarSinOt_quitaElIncidenteSinGenerarOt', async () => {
    renderizarRuta('/pendientes')
    const fila = (await screen.findByText('INC-0004')).closest('tr')!

    fireEvent.click(within(fila).getByRole('button', { name: 'Cerrar sin OT' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Cerrar sin OT' })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Cerrar sin OT' }))

    expect(await screen.findByText('No hay incidentes en espera de tercero.')).toBeVisible()
    expect(base.incidentes[0].estado).toBe('CERRADO_SIN_OT')
    expect(base.ordenes).toHaveLength(3)
  })

  it('jefatura_veLaOpcionEnElMenu', async () => {
    base.idSesion = ID_JEFATURA
    renderizarRuta('/')

    expect(await screen.findByRole('link', { name: 'Pendientes' })).toBeVisible()
  })

  it('tecnico_noEntraAlTablero', async () => {
    base.idSesion = ID_TECNICO
    const { router } = renderizarRuta('/pendientes')

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(screen.queryByRole('link', { name: 'Pendientes' })).not.toBeInTheDocument()
  })

  it('jefatura_anulaUnaOtConMotivoYSaleDelTablero', async () => {
    base.idSesion = ID_JEFATURA
    renderizarRuta('/pendientes')
    const fila = (await screen.findByRole('link', { name: 'OT-0001' })).closest('tr')!

    fireEvent.click(within(fila).getByRole('button', { name: 'Anular' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Anular OT-0001' })
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'DUPLICADA' } })
    fireEvent.change(within(dialogo).getByLabelText('Detalle'), {
      target: { value: 'misma falla que OT-0003' },
    })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar anulación' }))

    await waitFor(() => expect(numerosDelTablero()).toEqual(['OT-0002', 'OT-0003']))
    expect(base.ordenes.find((o) => o.id === 1)?.estado).toBe('ANULADA')
  })

  it('anular_otroSinDetalle_pideElDetalleYNoAnula', async () => {
    base.idSesion = ID_JEFATURA
    renderizarRuta('/pendientes')
    const fila = (await screen.findByRole('link', { name: 'OT-0003' })).closest('tr')!

    fireEvent.click(within(fila).getByRole('button', { name: 'Anular' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Anular OT-0003' })
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'OTRO' } })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar anulación' }))

    expect(await within(dialogo).findByText('Detallá el motivo.')).toBeVisible()
    expect(base.ordenes.find((o) => o.id === 3)?.estado).toBe('PENDIENTE')
  })

  it('enPausaDesdeEnProceso_noOfreceAnular', async () => {
    base.idSesion = ID_JEFATURA
    base.ordenes.find((o) => o.id === 2)!.estadoAnteriorPausa = 'EN_PROCESO'
    renderizarRuta('/pendientes')

    const fila = (await screen.findByRole('link', { name: 'OT-0002' })).closest('tr')!
    expect(within(fila).queryByRole('button', { name: 'Anular' })).not.toBeInTheDocument()
  })

  it('operador_noVeLaAccionDeAnular', async () => {
    renderizarRuta('/pendientes')
    await screen.findByRole('link', { name: 'OT-0001' })

    expect(screen.queryByRole('button', { name: 'Anular' })).not.toBeInTheDocument()
    const [tablero] = screen.getAllByRole('table')
    expect(
      within(tablero).queryByRole('columnheader', { name: 'Acciones' }),
    ).not.toBeInTheDocument()
  })
})
