import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): Norte tiene técnicos activos, Oeste no tiene
// ninguno y Centro está inactiva.
const ID_JEFATURA = 1
const ID_NORTE = 1
const ID_OESTE = 4
const ID_CENTRO = 5

function cuadrilla(id: number) {
  return base.cuadrillas.find((c) => c.id === id)!
}

function tecnicosActivosDe(id: number) {
  return base.usuarios.filter((u) => u.rol === 'TECNICO' && u.activo && u.idCuadrilla === id).length
}

async function fila(nombre: string) {
  return screen.findByRole('row', { name: new RegExp(nombre) })
}

function escribir(etiqueta: string, valor: string) {
  fireEvent.change(screen.getByLabelText(etiqueta), { target: { value: valor } })
}

describe('cuadrillas (#49)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  // --- Listado ---

  it('listado_muestraNombreZonaEstadoYTecnicosActivos', async () => {
    renderizarRuta('/cuadrillas')

    const norte = await fila('Cuadrilla Norte')
    expect(within(norte).getByText('Zona Norte')).toBeInTheDocument()
    expect(within(norte).getByText('Activo')).toBeInTheDocument()
    expect(within(norte).getByText(String(tecnicosActivosDe(ID_NORTE)))).toBeInTheDocument()

    const centro = await fila('Cuadrilla Centro')
    expect(within(centro).getByText('Inactivo')).toBeInTheDocument()
    expect(within(centro).getByRole('button', { name: 'Activar Cuadrilla Centro' })).toBeVisible()
  })

  // --- Alta ---

  it('nueva_sinNombre_muestraElErrorEnElCampo', async () => {
    renderizarRuta('/cuadrillas/nueva')
    await screen.findByRole('heading', { name: 'Nueva cuadrilla' })

    fireEvent.click(screen.getByRole('button', { name: 'Crear cuadrilla' }))

    expect(await screen.findByText('Ingresá el nombre.')).toBeInTheDocument()
  })

  it('nueva_datosValidos_laCreaYVuelveAlListado', async () => {
    const { router } = renderizarRuta('/cuadrillas/nueva')
    await screen.findByRole('heading', { name: 'Nueva cuadrilla' })
    escribir('Nombre', 'Cuadrilla Sur Grande')
    escribir('Zona (opcional)', 'Zona Sur')

    fireEvent.click(screen.getByRole('button', { name: 'Crear cuadrilla' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Cuadrilla Cuadrilla Sur Grande creada.',
    )
    expect(router.state.location.pathname).toBe('/cuadrillas')
    expect(await fila('Cuadrilla Sur Grande')).toBeInTheDocument()
    expect(base.cuadrillas.some((c) => c.nombre === 'Cuadrilla Sur Grande' && c.activa)).toBe(true)
  })

  it('nueva_nombreDuplicado_muestraElErrorJuntoAlNombre', async () => {
    renderizarRuta('/cuadrillas/nueva')
    await screen.findByRole('heading', { name: 'Nueva cuadrilla' })
    escribir('Nombre', 'cuadrilla norte')

    fireEvent.click(screen.getByRole('button', { name: 'Crear cuadrilla' }))

    const nombre = screen.getByLabelText('Nombre')
    await waitFor(() => expect(nombre).toHaveAttribute('aria-invalid', 'true'))
    expect(nombre).toHaveAccessibleDescription(
      'Ya existe una cuadrilla con el nombre cuadrilla norte.',
    )
  })

  // --- Edición ---

  it('editar_cuadrilla_muestraSusDatosYGuardaLosCambios', async () => {
    renderizarRuta(`/cuadrillas/${ID_OESTE}/editar`)
    await screen.findByRole('heading', { name: 'Editar cuadrilla' })
    expect(screen.getByLabelText('Nombre')).toHaveValue('Cuadrilla Oeste')

    escribir('Zona (opcional)', 'Zona Oeste y Costa')
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Cambios guardados.')
    expect(cuadrilla(ID_OESTE).zona).toBe('Zona Oeste y Costa')
  })

  // --- Desactivar y activar ---

  it('desactivar_cancelar_noCambiaNada', async () => {
    renderizarRuta('/cuadrillas')

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar Cuadrilla Oeste' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(cuadrilla(ID_OESTE).activa).toBe(true)
  })

  it('desactivar_sinTecnicos_quedaInactiva', async () => {
    renderizarRuta('/cuadrillas')

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar Cuadrilla Oeste' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desactivar' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Cuadrilla Cuadrilla Oeste desactivada.',
    )
    expect(
      await screen.findByRole('button', { name: 'Activar Cuadrilla Oeste' }),
    ).toBeInTheDocument()
    expect(cuadrilla(ID_OESTE).activa).toBe(false)
  })

  it('desactivar_conTecnicosActivos_muestraCuantosTieneYSigueActiva', async () => {
    const cantidad = tecnicosActivosDe(ID_NORTE)
    renderizarRuta('/cuadrillas')

    fireEvent.click(await screen.findByRole('button', { name: 'Desactivar Cuadrilla Norte' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desactivar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      `La cuadrilla Cuadrilla Norte tiene ${cantidad} técnicos activos. Primero cambialos de cuadrilla.`,
    )
    expect(cuadrilla(ID_NORTE).activa).toBe(true)
  })

  it('activar_cuadrillaInactiva_quedaActiva', async () => {
    renderizarRuta('/cuadrillas')

    fireEvent.click(await screen.findByRole('button', { name: 'Activar Cuadrilla Centro' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Activar' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Cuadrilla Cuadrilla Centro activada.',
    )
    expect(
      await screen.findByRole('button', { name: 'Desactivar Cuadrilla Centro' }),
    ).toBeInTheDocument()
    expect(cuadrilla(ID_CENTRO).activa).toBe(true)
  })
})
