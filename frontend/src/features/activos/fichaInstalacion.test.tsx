import { fireEvent, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base, type OrdenSimulada } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts):
// - INST-001 tiene tipo y dirección; las demás, no.
// - OT-0001: asignada a Norte, en INST-001, de INC-0001 (media). OT-0003: pendiente sin SLA, en
//   INST-003. INST-005 no tiene OTs.
const ID_JEFATURA = 1
const ID_TECNICO_NORTE = 2
const ID_OPERADOR = 3
const HORA = 3_600_000

/** El valor de un dato de la instalación (no de sus OTs): el dd que sigue a su dt. */
function dato(termino: string) {
  return screen.getByText(termino, { selector: '.ficha-datos dt' }).nextElementSibling
}

/** Una OT más en la instalación, sobre la base de OT-0001. */
function agregarOrden(cambios: Partial<OrdenSimulada>) {
  base.ordenes.push({ ...base.ordenes[0], pausas: [], idIncidente: null, ...cambios })
}

function tarjeta(numero: string) {
  return screen.getByText(numero).closest('li')!
}

describe('ficha de la instalación (HU-10)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('datos_sinTipoNiDireccion_muestraSinDatosYElOrganismo', async () => {
    renderizarRuta('/instalaciones/2')

    expect(
      await screen.findByRole('heading', { name: 'INST-002 — Cruce Ficticio 2' }),
    ).toBeInTheDocument()
    expect(dato('Tipo')).toHaveTextContent('Sin datos')
    expect(dato('Dirección')).toHaveTextContent('Sin datos')
    expect(dato('Ruta o zona')).toHaveTextContent('Ruta 5')
    expect(dato('Coordenadas')).toHaveTextContent('-34,887655, -56,176544')
    expect(dato('Organismo')).toHaveTextContent('MTOP')
    expect(screen.queryByText('Inactiva')).not.toBeInTheDocument()
  })

  it('datos_conTipoYDireccion_losMuestra', async () => {
    renderizarRuta('/instalaciones/1')

    expect(await screen.findByText('Semáforo vehicular')).toBeInTheDocument()
    expect(screen.getByText('Av. Ficticia 1234')).toBeInTheDocument()
  })

  it('mapa_marcaLaInstalacion', async () => {
    renderizarRuta('/instalaciones/1')

    const mapa = await screen.findByRole('region', { name: 'Mapa de instalaciones' })
    expect(within(mapa).getByRole('button', { name: 'INST-001 — Cruce Ficticio 1' })).toHaveClass(
      'mapa-marcador-elegido',
    )
  })

  it('inactiva_seConsultaConLaMarca', async () => {
    base.instalaciones[4].activa = false
    renderizarRuta('/instalaciones/5')

    expect(await screen.findByText('Inactiva')).toBeInTheDocument()
  })

  it('otsEnCurso_muestraLosDatosYLlevaAlDetalle', async () => {
    const { router } = renderizarRuta('/instalaciones/1')

    await screen.findByText('OT-0001')
    const ot = within(tarjeta('OT-0001'))
    expect(ot.getByText('Asignada')).toBeInTheDocument()
    expect(ot.getByText('Correctiva (INC-0001)')).toBeInTheDocument()
    expect(ot.getByText('Media')).toBeInTheDocument()
    expect(ot.getByText('Cuadrilla Norte')).toBeInTheDocument()

    fireEvent.click(ot.getByRole('link', { name: 'OT-0001' }))

    expect(await screen.findByRole('heading', { name: 'Orden OT-0001' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ordenes/1')
  })

  it('otsEnCurso_vencidaYSinSla_seMarcanEnOrden', async () => {
    agregarOrden({
      id: 50,
      numero: 'OT-0050',
      estado: 'PENDIENTE',
      idCuadrilla: null,
      fechaLimite: new Date(Date.now() - 2 * HORA).toISOString(),
    })
    agregarOrden({ id: 51, numero: 'OT-0051', estado: 'PENDIENTE', fechaLimite: null })
    agregarOrden({ id: 52, numero: 'OT-0052', estado: 'CERRADA_TECNICA' })
    renderizarRuta('/instalaciones/1')

    await screen.findByText('OT-0050')
    const numeros = within(screen.getByRole('region', { name: 'OTs en curso' }))
      .getAllByRole('listitem')
      .map((li) => li.querySelector('.ficha-orden-numero')?.textContent)
    expect(numeros).toEqual(['OT-0050', 'OT-0001', 'OT-0051'])
    expect(within(tarjeta('OT-0050')).getByText('Vencida')).toBeInTheDocument()
    expect(within(tarjeta('OT-0050')).getByText('Sin asignar')).toBeInTheDocument()
    expect(within(tarjeta('OT-0051')).getByText('SLA sin definir')).toBeInTheDocument()
    expect(screen.queryByText('OT-0052')).not.toBeInTheDocument()
  })

  it('otsEnCurso_tecnico_soloEnlazaLasDeSuCuadrilla', async () => {
    base.idSesion = ID_TECNICO_NORTE
    agregarOrden({ id: 60, numero: 'OT-0060', idCuadrilla: 2 })
    renderizarRuta('/instalaciones/1')

    expect(await screen.findByRole('link', { name: 'OT-0001' })).toBeInTheDocument()
    expect(screen.getByText('OT-0060')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'OT-0060' })).not.toBeInTheDocument()
    expect(within(tarjeta('OT-0060')).getByText('Cuadrilla Sur')).toBeInTheDocument()
  })

  it('otsEnCurso_sinOts_loInforma', async () => {
    base.idSesion = ID_OPERADOR
    renderizarRuta('/instalaciones/5')

    expect(await screen.findByText('No hay OTs en curso en esta instalación.')).toBeInTheDocument()
  })

  it('inexistente_muestraQueNoExiste', async () => {
    renderizarRuta('/instalaciones/999')

    expect(await screen.findByRole('alert')).toHaveTextContent('No existe la instalación.')
  })
})
