import { fireEvent, screen, within } from '@testing-library/react'
import { http } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts):
// - INST-001: OT-0001 en curso (Norte); en el historial, OT-0006 (preventiva anulada, hace 119 h),
//   OT-0004 (cerrada técnicamente por Norte, hace 70 h) y OT-0005 (validada, hace 236 h).
// - INST-004: INC-0004 en espera de UTE (hace 3 h) e INC-0007 cerrado sin OT (hace 96 h).
// - INST-005: sin historia.
const ID_JEFATURA = 1
const ID_TECNICO_SUR = 11 // usuario de relleno: técnico activo de la cuadrilla 2
const HORA = 3_600_000

function historial() {
  return within(screen.getByRole('region', { name: 'Historial' }))
}

function entrada(numero: string) {
  return within(historial().getByText(numero).closest('li')!)
}

function numeros() {
  return historial()
    .getAllByRole('listitem')
    .map((li) => li.querySelector('.historial-numero')?.textContent)
}

describe('historial de la instalación (HU-10)', () => {
  beforeEach(() => {
    base.idSesion = ID_JEFATURA
  })

  it('otsTerminadas_deLaMasNuevaALaMasViejaYSinLasEnCurso', async () => {
    renderizarRuta('/instalaciones/1')

    await screen.findByText('OT-0004')
    expect(numeros()).toEqual(['OT-0004', 'OT-0006', 'OT-0005'])
    expect(historial().queryByText('OT-0001')).not.toBeInTheDocument()
  })

  it('cadaEntrada_muestraSuMarcaYSusDatos', async () => {
    renderizarRuta('/instalaciones/1')

    await screen.findByText('OT-0004')
    const cerrada = entrada('OT-0004')
    expect(cerrada.getByText('Cerrada técnicamente, falta validar')).toBeInTheDocument()
    expect(cerrada.getByText('Correctiva (INC-0005)')).toBeInTheDocument()
    expect(cerrada.getByText('Cable de alimentación cortado')).toBeInTheDocument()
    expect(cerrada.getByText('Se reemplazó el tramo de cable')).toBeInTheDocument()
    expect(cerrada.getByText('Cuadrilla Norte')).toBeInTheDocument()
    expect(cerrada.getByText(/^Cerrada el /)).toBeInTheDocument()

    const validada = entrada('OT-0005')
    expect(validada.getByText('Validada')).toBeInTheDocument()
    expect(validada.getByText('Quedó funcionando')).toBeInTheDocument()

    const anulada = entrada('OT-0006')
    expect(anulada.getByText('Anulada')).toBeInTheDocument()
    expect(anulada.getByText('Preventiva')).toBeInTheDocument()
    expect(anulada.getByText('Duplicada: misma falla que OT-0004')).toBeInTheDocument()
    expect(anulada.getByText(/^Anulada el /)).toBeInTheDocument()
  })

  it('incidentesSinOt_muestranElTerceroYLaFechaDeReporte', async () => {
    renderizarRuta('/instalaciones/4')

    await screen.findByText('INC-0007')
    expect(numeros()).toEqual(['INC-0004', 'INC-0007'])
    const enEspera = entrada('INC-0004')
    expect(enEspera.getByText('En espera de tercero')).toBeInTheDocument()
    expect(enEspera.getByText('UTE')).toBeInTheDocument()
    expect(enEspera.getByText('Corte de energía en la zona')).toBeInTheDocument()
    expect(enEspera.getByText(/^Reportado el /)).toBeInTheDocument()
    expect(enEspera.queryByRole('link')).not.toBeInTheDocument()
    expect(entrada('INC-0007').getByText('Cerrado sin OT')).toBeInTheDocument()
  })

  it('otTerminada_llevaAlDetalle', async () => {
    const { router } = renderizarRuta('/instalaciones/1')

    fireEvent.click(await screen.findByRole('link', { name: 'OT-0004' }))

    expect(await screen.findByRole('heading', { name: 'Orden OT-0004' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/ordenes/4')
  })

  it('tecnicoDeOtraCuadrilla_veElDetalleDeLasTerminadas', async () => {
    base.idSesion = ID_TECNICO_SUR
    renderizarRuta('/instalaciones/1')

    expect(await screen.findByRole('link', { name: 'OT-0004' })).toBeInTheDocument()
    expect(historial().getByRole('link', { name: 'OT-0005' })).toBeInTheDocument()
    // La en curso de Norte, en la ficha, sigue sin enlace.
    expect(screen.queryByRole('link', { name: 'OT-0001' })).not.toBeInTheDocument()
  })

  it('anularUnaEnCurso_pasaAlHistorialConSuMotivo', async () => {
    const orden = base.ordenes.find((o) => o.id === 1)!
    orden.estado = 'ANULADA'
    orden.motivoAnulacion = 'Reclamo repetido'
    orden.anuladaEn = new Date(Date.now() - HORA).toISOString()
    renderizarRuta('/instalaciones/1')

    await screen.findByText('OT-0001')
    expect(numeros()[0]).toBe('OT-0001')
    expect(entrada('OT-0001').getByText('Reclamo repetido')).toBeInTheDocument()
    expect(screen.getByText('No hay OTs en curso en esta instalación.')).toBeInTheDocument()
  })

  it('sinIntervenciones_loInforma', async () => {
    renderizarRuta('/instalaciones/5')

    expect(
      await screen.findByText('Esta instalación no tiene intervenciones registradas.'),
    ).toBeInTheDocument()
  })

  it('variasPaginas_pasaALaSiguiente', async () => {
    for (let i = 0; i < 20; i++) {
      base.incidentes.push({
        ...base.incidentes[6],
        id: 100 + i,
        numero: `INC-${100 + i}`,
        creadoEn: new Date(Date.now() - (200 + i) * HORA).toISOString(),
      })
    }
    const { router } = renderizarRuta('/instalaciones/4')

    expect(await screen.findByText('Página 1 de 2 (22 en total)')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }))

    expect(await screen.findByText('Página 2 de 2 (22 en total)')).toBeInTheDocument()
    expect(numeros()).toEqual(['INC-118', 'INC-119'])
    expect(router.state.location.search).toBe('?pagina=2')
  })

  it('errorDelHistorial_laFichaSigueALaVista', async () => {
    servidor.use(
      http.get('/api/v1/instalaciones/:id/historial', () =>
        problema(500, 'regla-negocio', 'Error', 'Falló.'),
      ),
    )
    renderizarRuta('/instalaciones/1')

    expect(await historialAlerta()).toHaveTextContent('No se pudo cargar el historial')
    expect(screen.getByRole('heading', { name: 'INST-001 — Cruce Ficticio 1' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'OT-0001' })).toBeInTheDocument()
  })
})

async function historialAlerta() {
  await screen.findByRole('region', { name: 'Historial' })
  return historial().findByRole('alert')
}
