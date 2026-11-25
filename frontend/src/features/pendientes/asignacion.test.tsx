import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { formatearFechaMontevideo, hoyEnMontevideo } from '../../shared/lib/fechas'
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

/**
 * Marca las OTs, abre el diálogo de asignación y espera a que carguen las cuadrillas. El título
 * por defecto es el de asignar; al reasignar (todas ya asignadas, #87) se pasa el esperado.
 */
async function abrirDialogo(numeros: string[], titulo = tituloAsignar(numeros)) {
  for (const numero of numeros) {
    fireEvent.click(within(await fila(numero)).getByRole('checkbox'))
  }
  fireEvent.click(screen.getByRole('button', { name: `Asignar (${numeros.length})` }))
  const dialogo = await screen.findByRole('dialog', { name: titulo })
  // Sin esto, elegir una cuadrilla antes de que lleguen las opciones no tiene efecto.
  await within(dialogo).findByRole('option', { name: /Cuadrilla Sur/ })
  return dialogo
}

function tituloAsignar(numeros: string[]) {
  return numeros.length === 1 ? `Asignar ${numeros[0]}` : `Asignar ${numeros.length} OTs`
}

/** Hoy en dd/mm/aaaa, como lo muestra el diálogo. */
function hoyMostrado() {
  return formatearFechaMontevideo(hoyEnMontevideo())
}

describe('asignar OTs desde el tablero (#41)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('variasOts_lasAsignaALaCuadrillaYElTableroLoMuestra', async () => {
    renderizarRuta('/pendientes')
    // Mixta (una asignada y una pendiente): se titula «Asignar», pero muestra la asignación actual
    // de la que ya tiene cuadrilla (#87).
    const dialogo = await abrirDialogo(['OT-0001', 'OT-0003'])
    const actual = within(dialogo).getByRole('region', { name: 'Asignación actual' })
    expect(
      within(actual)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual([`OT-0001: Cuadrilla Norte, ${hoyMostrado()}`])

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

  it('soloPendientes_seTitulaAsignarSinAsignacionActual', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0003'])

    expect(within(dialogo).getByRole('button', { name: 'Asignar' })).toBeVisible()
    expect(within(dialogo).queryByRole('region', { name: 'Asignación actual' })).toBeNull()
  })

  it('unaAsignada_seTitulaReasignarYMuestraCuadrillaYFecha', async () => {
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0001'], 'Reasignar OT-0001')

    expect(within(dialogo).getByRole('button', { name: 'Reasignar' })).toBeVisible()
    expect(
      within(within(dialogo).getByRole('region', { name: 'Asignación actual' })).getByText(
        `OT-0001: Cuadrilla Norte, ${hoyMostrado()}`,
      ),
    ).toBeVisible()
  })

  it('todasAsignadas_seTitulaReasignarVarias', async () => {
    // En los datos simulados solo OT-0001 está asignada (OT-0002 está en pausa): se asigna también
    // la OT-0003 a Cuadrilla Sur para mañana.
    const manana = hoyEnMontevideo(new Date(Date.now() + DIA))
    Object.assign(
      base.ordenes.find((o) => o.id === 3)!,
      {
        estado: 'ASIGNADA',
        idCuadrilla: 2,
        fechaEjecucion: manana,
      },
    )
    renderizarRuta('/pendientes')
    const dialogo = await abrirDialogo(['OT-0001', 'OT-0003'], 'Reasignar 2 OTs')

    expect(within(dialogo).getByRole('button', { name: 'Reasignar' })).toBeVisible()
    const actual = within(dialogo).getByRole('region', { name: 'Asignación actual' })
    expect(
      within(actual)
        .getAllByRole('listitem')
        .map((li) => li.textContent),
    ).toEqual([
      `OT-0001: Cuadrilla Norte, ${hoyMostrado()}`,
      `OT-0003: Cuadrilla Sur, ${formatearFechaMontevideo(manana)}`,
    ])
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
