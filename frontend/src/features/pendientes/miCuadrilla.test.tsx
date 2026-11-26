import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { reglaNegocio } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): el Técnico de Prueba es de Cuadrilla Norte, que
// tiene OT-0001 (asignada) y OT-0002 (en pausa). Cuadrilla Centro está inactiva.
const ID_TECNICO_NORTE = 2
const ID_SUR = 2

function tecnico() {
  return base.usuarios.find((u) => u.id === ID_TECNICO_NORTE)!
}

async function abrirDialogo() {
  fireEvent.click(await screen.findByRole('button', { name: 'Cambiar de cuadrilla' }))
  return within(await screen.findByRole('dialog', { name: 'Cambiar de cuadrilla' }))
}

describe('cambiar de cuadrilla desde «Mis OTs» (HU-20)', () => {
  beforeEach(() => {
    base.idSesion = ID_TECNICO_NORTE
  })

  it('muestraLaCuadrillaActual', async () => {
    renderizarRuta('/mis-ots')

    expect(await screen.findByText('Cuadrilla Norte')).toBeVisible()
  })

  it('dialogo_listaSoloLasActivasConLaActualMarcada', async () => {
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    const opciones = dialogo.getAllByRole('radio')
    expect(opciones).toHaveLength(4)
    expect(dialogo.queryByLabelText(/Cuadrilla Centro/)).not.toBeInTheDocument()
    expect(dialogo.getByLabelText(/Cuadrilla Norte \(tu cuadrilla actual\)/)).toBeChecked()
    // Con la actual elegida no hay nada que cambiar.
    expect(dialogo.getByRole('button', { name: 'Cambiarme' })).toBeDisabled()
  })

  it('aviso_conOtsEnCurso_lasDiceAparte', async () => {
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    expect(
      dialogo.getByText(
        'Vas a dejar de ver 2 OTs de tu cuadrilla en Mis OTs (1 en proceso o en pausa).',
      ),
    ).toBeVisible()
  })

  it('aviso_soloAsignadas_sinParentesis', async () => {
    base.ordenes = base.ordenes.filter((o) => o.id !== 2)
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    expect(dialogo.getByText('Vas a dejar de ver 1 OT de tu cuadrilla en Mis OTs.')).toBeVisible()
  })

  it('aviso_sinOts_noAparece', async () => {
    base.ordenes = base.ordenes.filter((o) => o.idCuadrilla !== 1)
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    expect(dialogo.queryByText(/Vas a dejar de ver/)).not.toBeInTheDocument()
  })

  it('cambiar_actualizaLaCuadrillaYLaBandeja', async () => {
    const deSur = { ...base.ordenes[0], id: 50, numero: 'OT-0050', idCuadrilla: ID_SUR }
    base.ordenes.push(deSur)
    renderizarRuta('/mis-ots')
    await screen.findByRole('listitem', { name: 'OT-0001' })
    const dialogo = await abrirDialogo()

    fireEvent.click(dialogo.getByLabelText(/Cuadrilla Sur/))
    fireEvent.click(dialogo.getByRole('button', { name: 'Cambiarme' }))

    expect(await screen.findByText('Ahora estás en Cuadrilla Sur.')).toBeVisible()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(await screen.findByRole('listitem', { name: 'OT-0050' })).toBeVisible()
    expect(screen.queryByRole('listitem', { name: 'OT-0001' })).not.toBeInTheDocument()
    expect(screen.getByText('Cuadrilla Sur', { selector: 'strong' })).toBeVisible()
    expect(tecnico().idCuadrilla).toBe(ID_SUR)
  })

  it('cancelar_noCambiaNada', async () => {
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    fireEvent.click(dialogo.getByLabelText(/Cuadrilla Sur/))
    fireEvent.click(dialogo.getByRole('button', { name: 'Cancelar' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(tecnico().idCuadrilla).toBe(1)
    expect(screen.getByText('Cuadrilla Norte', { selector: 'strong' })).toBeVisible()
  })

  it('errorDelBackend_seMuestraEnElDialogo', async () => {
    servidor.use(
      http.post('/api/v1/mi-cuadrilla/cambiar', () =>
        reglaNegocio('La cuadrilla Cuadrilla Sur está inactiva.'),
      ),
    )
    renderizarRuta('/mis-ots')
    const dialogo = await abrirDialogo()

    fireEvent.click(dialogo.getByLabelText(/Cuadrilla Sur/))
    fireEvent.click(dialogo.getByRole('button', { name: 'Cambiarme' }))

    expect(await dialogo.findByRole('alert')).toHaveTextContent(
      'La cuadrilla Cuadrilla Sur está inactiva.',
    )
    expect(tecnico().idCuadrilla).toBe(1)
  })
})
