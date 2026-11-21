import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { hoyEnMontevideo } from '../../shared/lib/fechas'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): OT-0001 asignada a Cuadrilla Norte para hoy,
// OT-0002 en pausa por UTE (también de Norte, para hoy) y OT-0003 pendiente sin cuadrilla.
// Cuadrilla Centro está inactiva.
const ID_JEFATURA = 1
const ID_OPERADOR = 3
const DIA = 86_400_000

/** Fila del tablero de una OT. */
async function fila(numero: string) {
  return (await screen.findByRole('link', { name: numero })).closest('tr')!
}

/** Marca las OTs, abre el diálogo de asignación y espera a que carguen las cuadrillas. */
async function abrirDialogo(numeros: string[]) {
  for (const numero of numeros) {
    fireEvent.click(within(await fila(numero)).getByRole('checkbox'))
  }
  fireEvent.click(screen.getByRole('button', { name: `Asignar (${numeros.length})` }))
  const titulo = numeros.length === 1 ? `Asignar ${numeros[0]}` : `Asignar ${numeros.length} OTs`
  const dialogo = await screen.findByRole('dialog', { name: titulo })
  // Sin esto, elegir una cuadrilla antes de que lleguen las opciones no tiene efecto.
  await within(dialogo).findByRole('option', { name: /Cuadrilla Sur/ })
  return dialogo
}

describe('asignar OTs desde el tablero (#41)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('variasOts_lasAsignaALaCuadrillaYElTableroLoMuestra', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0001', 'OT-0003'])

    fireEvent.change(within(dialogo).getByLabelText('Cuadrilla'), { target: { value: '2' } })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Asignar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await within(await fila('OT-0003')).findByText('Cuadrilla Sur')).toBeVisible()
    for (const id of [1, 3]) {
      const orden = base.ordenes.find((o) => o.id === id)!
      expect(orden.estado).toBe('ASIGNADA')
      expect(orden.idCuadrilla).toBe(2)
      expect(orden.fechaEjecucion).toBe(hoyEnMontevideo())
    }
    // Se limpia la selección.
    expect(screen.getByRole('button', { name: 'Asignar' })).toBeDisabled()
  })

  it('dialogo_muestraLaCargaDeCadaCuadrillaParaLaFechaElegida', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0003'])

    // Hoy: OT-0001 y OT-0002 son de Norte.
    expect(
      await within(dialogo).findByRole('option', { name: 'Cuadrilla Norte — 2 OTs' }),
    ).toBeInTheDocument()
    expect(
      within(dialogo).getByRole('option', { name: 'Cuadrilla Sur — 0 OTs' }),
    ).toBeInTheDocument()

    const manana = hoyEnMontevideo(new Date(Date.now() + DIA))
    fireEvent.change(within(dialogo).getByLabelText('Fecha de ejecución'), {
      target: { value: manana },
    })

    expect(
      await within(dialogo).findByRole('option', { name: 'Cuadrilla Norte — 0 OTs' }),
    ).toBeInTheDocument()
  })

  it('dialogo_noOfreceCuadrillasInactivas', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0003'])

    await within(dialogo).findByRole('option', { name: /Cuadrilla Norte/ })
    expect(within(dialogo).queryByRole('option', { name: /Cuadrilla Centro/ })).toBeNull()
  })

  it('fechaAnterior_muestraElErrorEnElCampoYNoAsigna', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0003'])

    const ayer = hoyEnMontevideo(new Date(Date.now() - DIA))
    fireEvent.change(within(dialogo).getByLabelText('Fecha de ejecución'), {
      target: { value: ayer },
    })
    fireEvent.change(within(dialogo).getByLabelText('Cuadrilla'), { target: { value: '2' } })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Asignar' }))

    expect(await within(dialogo).findByText('La fecha no puede ser anterior a hoy.')).toBeVisible()
    expect(base.ordenes.find((o) => o.id === 3)?.estado).toBe('PENDIENTE')
  })

  it('sinCuadrilla_pideLaCuadrilla', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0003'])

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Asignar' }))

    expect(await within(dialogo).findByText('Elegí la cuadrilla.')).toBeVisible()
  })

  it('unaCambioMientrasTanto_muestraElErrorConSuNumeroYNoAsignaNinguna', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0001', 'OT-0003'])
    // Mientras el diálogo está abierto, un técnico empieza la OT-0003.
    base.ordenes.find((o) => o.id === 3)!.estado = 'EN_PROCESO'

    fireEvent.change(within(dialogo).getByLabelText('Cuadrilla'), { target: { value: '2' } })
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Asignar' }))

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'OT-0003: No se puede asignar una OT en estado EN_PROCESO.',
    )
    // Todas o ninguna: la OT-0001 sigue con su cuadrilla.
    expect(base.ordenes.find((o) => o.id === 1)?.idCuadrilla).toBe(1)
  })

  it('soloOfreceCasillaEnLasQueSePuedenAsignar', async () => {
    renderizarRuta('/pendientes')

    expect(within(await fila('OT-0001')).getByRole('checkbox')).toBeVisible()
    expect(within(await fila('OT-0003')).getByRole('checkbox')).toBeVisible()
    // En pausa: no se asigna.
    expect(within(await fila('OT-0002')).queryByRole('checkbox')).toBeNull()
  })

  it('quitarSeleccion_desmarcaYDeshabilitaAsignar', async () => {
    renderizarRuta('/pendientes')
    fireEvent.click(within(await fila('OT-0003')).getByRole('checkbox'))
    expect(screen.getByRole('button', { name: 'Asignar (1)' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Quitar selección' }))

    expect(screen.getByRole('button', { name: 'Asignar' })).toBeDisabled()
    expect(within(await fila('OT-0003')).getByRole('checkbox')).not.toBeChecked()
  })

  it('operador_noVeCasillasNiElBoton', async () => {
    base.idSesion = ID_OPERADOR
    renderizarRuta('/pendientes')
    await fila('OT-0001')

    expect(screen.queryByRole('checkbox')).toBeNull()
    expect(screen.queryByRole('button', { name: /^Asignar/ })).toBeNull()
  })
})
