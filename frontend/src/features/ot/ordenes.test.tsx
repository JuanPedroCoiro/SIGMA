import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import {
  formatearFechaHoraMontevideo,
  formatearFechaMontevideo,
  hoyEnMontevideo,
} from '../../shared/lib/fechas'
import { renderizarRuta } from '../../test/renderizarRuta'
import { formatearDuracion } from './duracion'
import { ETIQUETA_ORIGEN, ETIQUETA_PRIORIDAD } from './types'

// Datos de la API simulada (src/mocks/datos.ts):
// - OT-0001: asignada a Norte para hoy, INST-001, de INC-0001 (reclamo, «Lámpara quemada», media).
// - OT-0002: en pausa por UTE (la pausó el Operador de Prueba), cuadrilla Norte, de INC-0002.
// - OT-0003: pendiente sin cuadrilla, fecha de ejecución ni SLA, de INC-0003.
const ID_OPERADOR = 3
const ID_TECNICO_NORTE = 2
const ID_TECNICO_SUR = 11 // usuario de relleno: técnico activo de la cuadrilla 2

function orden(id: number) {
  return base.ordenes.find((o) => o.id === id)!
}

async function abrirDialogoPausa() {
  fireEvent.click(await screen.findByRole('button', { name: 'Pausar' }))
  return screen.findByRole('dialog', { name: /Pausar OT-/ })
}

describe('detalle de OT y pausas (#39)', () => {
  beforeEach(() => {
    base.idSesion = ID_OPERADOR
  })

  it('detalle_muestraEstadoFechaLimiteYPausaActual', async () => {
    renderizarRuta('/ordenes/2')

    expect(await screen.findByRole('heading', { name: 'Orden OT-0002' })).toBeInTheDocument()
    expect(screen.getByText('En pausa', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('En pausa: Falla de un tercero')
    expect(screen.getByRole('status')).toHaveTextContent('(UTE)')
    // En pausa por un tercero no vence, aunque ya pasó la fecha límite.
    expect(screen.queryByText('Vencida')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reanudar' })).toBeVisible()
  })

  it('historial_desdeYHastaConFechaYHoraAunqueSeaHoy', async () => {
    const inicio = new Date().toISOString()
    orden(2).pausas[0].inicio = inicio
    renderizarRuta('/ordenes/2')

    const fila = await screen.findByRole('row', { name: /UTE/ })
    const celdas = within(fila).getAllByRole('cell')
    expect(celdas[2]).toHaveTextContent(formatearFechaHoraMontevideo(inicio))
    expect(celdas[2]).toHaveTextContent(/^\d{2}:\d{2} del \d{1,2}\/\d{1,2}$/)
    expect(celdas[3]).toHaveTextContent('En curso')
  })

  it('detalle_sinSla_loIndica', async () => {
    renderizarRuta('/ordenes/3')

    expect(await screen.findByText('SLA sin definir')).toBeInTheDocument()
    expect(screen.getByText('Sin pausas.')).toBeInTheDocument()
  })

  it('pausar_terceroSinDetalle_noEnviaYMarcaElCampo', async () => {
    renderizarRuta('/ordenes/1')
    const dialogo = await abrirDialogoPausa()
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'TERCERO' } })

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

    expect(await within(dialogo).findByText('Indicá qué tercero causa la pausa.')).toBeVisible()
    expect(within(dialogo).getByLabelText('Detalle')).toHaveAttribute('aria-invalid', 'true')
    expect(orden(1).estado).toBe('ASIGNADA')
    expect(orden(1).pausas).toHaveLength(0)
  })

  it('pausar_sinMotivo_pideElMotivo', async () => {
    renderizarRuta('/ordenes/1')
    const dialogo = await abrirDialogoPausa()

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

    expect(await within(dialogo).findByText('Elegí el motivo de la pausa.')).toBeVisible()
    expect(orden(1).pausas).toHaveLength(0)
  })

  it('pausar_terceroConDetalle_quedaEnPausa', async () => {
    renderizarRuta('/ordenes/1')
    const dialogo = await abrirDialogoPausa()
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'TERCERO' } })
    expect(within(dialogo).getByText(/vuelve a empezar completo al reanudar/)).toBeVisible()
    fireEvent.change(within(dialogo).getByLabelText('Detalle'), { target: { value: 'Antel' } })

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByRole('status')).toHaveTextContent('En pausa: Falla de un tercero')
    expect(screen.getByRole('button', { name: 'Reanudar' })).toBeVisible()
    expect(orden(1).estado).toBe('EN_PAUSA')
    expect(orden(1).pausas[0]).toMatchObject({ motivo: 'TERCERO', detalle: 'Antel' })
  })

  it('pausar_materialesSinDetalle_pausa', async () => {
    renderizarRuta('/ordenes/1')
    const dialogo = await abrirDialogoPausa()
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), {
      target: { value: 'MATERIALES' },
    })

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Falta de materiales')
    expect(orden(1).pausas[0]).toMatchObject({ motivo: 'MATERIALES', detalle: null })
  })

  it('reanudar_vuelveAlEstadoAnteriorYReiniciaElSla', async () => {
    const plazo =
      new Date(orden(2).fechaLimite!).getTime() - new Date(orden(2).inicioSla!).getTime()
    renderizarRuta('/ordenes/2')

    fireEvent.click(await screen.findByRole('button', { name: 'Reanudar' }))

    expect(await screen.findByText('Asignada', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pausar' })).toBeVisible()
    expect(screen.getByRole('cell', { name: 'UTE' })).toBeInTheDocument()
    const reanudada = orden(2)
    const hasta = within(screen.getByRole('row', { name: /UTE/ })).getAllByRole('cell')[3]
    expect(hasta).toHaveTextContent(formatearFechaHoraMontevideo(reanudada.pausas[0].fin!))
    expect(
      new Date(reanudada.fechaLimite!).getTime() - new Date(reanudada.inicioSla!).getTime(),
    ).toBe(plazo)
  })

  it('reanudar_errorDelServidor_muestraElDetalle', async () => {
    servidor.use(
      http.post('/api/v1/ordenes/:id/reanudar', () =>
        problema(
          400,
          'transicion-invalida',
          'Regla de negocio no cumplida',
          'No se puede reanudar una OT en estado ASIGNADA.',
        ),
      ),
    )
    renderizarRuta('/ordenes/2')

    fireEvent.click(await screen.findByRole('button', { name: 'Reanudar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se puede reanudar una OT en estado ASIGNADA.',
    )
  })

  it('pausar_errorDeReglaDelServidor_loMuestraEnElDialogo', async () => {
    servidor.use(
      http.post('/api/v1/ordenes/:id/pausar', () =>
        problema(
          400,
          'transicion-invalida',
          'Regla de negocio no cumplida',
          'No se puede pausar una OT en estado EN_PAUSA.',
        ),
      ),
    )
    renderizarRuta('/ordenes/1')
    const dialogo = await abrirDialogoPausa()
    fireEvent.change(within(dialogo).getByLabelText('Motivo'), { target: { value: 'CLIMA' } })

    fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent(
      'No se puede pausar una OT en estado EN_PAUSA.',
    )
  })

  it('tecnicoDeLaCuadrilla_puedeReanudar', async () => {
    base.idSesion = ID_TECNICO_NORTE
    renderizarRuta('/ordenes/2')

    fireEvent.click(await screen.findByRole('button', { name: 'Reanudar' }))

    expect(await screen.findByText('Asignada', { selector: 'dd' })).toBeInTheDocument()
  })

  it('tecnicoDeOtraCuadrilla_veElMensajeDeSinPermiso', async () => {
    base.idSesion = ID_TECNICO_SUR
    expect(base.usuarios.find((u) => u.id === ID_TECNICO_SUR)).toMatchObject({
      rol: 'TECNICO',
      idCuadrilla: 2,
    })
    renderizarRuta('/ordenes/1')

    expect(await screen.findByRole('alert')).toHaveTextContent('no está asignada a tu cuadrilla')
  })

  it('tecnicoDeOtraCuadrilla_otTerminada_veElDetalleSinPausar', async () => {
    // Revisa el historial del cruce antes de intervenir (HU-10): ve, pero no opera.
    base.idSesion = ID_TECNICO_SUR
    orden(1).estado = 'ANULADA'
    renderizarRuta('/ordenes/1')

    expect(await screen.findByRole('heading', { name: 'Orden OT-0001' })).toBeInTheDocument()
    expect(screen.getByText('Anulada', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pausar' })).not.toBeInTheDocument()
  })

  it('formatearDuracion_horasMinutosYDias', () => {
    expect(formatearDuracion(45)).toBe('45 min')
    expect(formatearDuracion(125)).toBe('2 h 05 min')
    expect(formatearDuracion(3 * 1440 + 4 * 60)).toBe('3 d 4 h')
  })
})

describe('detalle de OT con instalación, cuadrilla e incidente (#57)', () => {
  beforeEach(() => {
    base.idSesion = ID_OPERADOR
  })

  it('detalle_muestraInstalacionCuadrillaYFechaDeEjecucion', async () => {
    renderizarRuta('/ordenes/1')

    expect(
      await screen.findByRole('link', { name: 'INST-001 — Cruce Ficticio 1' }),
    ).toBeInTheDocument()
    expect(screen.getByText('Cuadrilla Norte', { selector: 'dd' })).toBeInTheDocument()
    expect(
      screen.getByText(formatearFechaMontevideo(hoyEnMontevideo()), { selector: 'dd' }),
    ).toBeInTheDocument()
  })

  it('detalle_instalacion_tecnico_llevaALaFicha', async () => {
    base.idSesion = ID_TECNICO_NORTE
    const { router } = renderizarRuta('/ordenes/1')

    fireEvent.click(await screen.findByRole('link', { name: 'INST-001 — Cruce Ficticio 1' }))

    expect(await screen.findByRole('heading', { name: 'OTs en curso' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/instalaciones/1')
  })

  it('detalle_sinCuadrilla_muestraSinAsignar', async () => {
    renderizarRuta('/ordenes/3')

    expect(await screen.findByText('Sin asignar', { selector: 'dd' })).toBeInTheDocument()
    const fechaEjecucion = screen.getByText('Fecha de ejecución').nextElementSibling
    expect(fechaEjecucion).toHaveTextContent('—')
  })

  it('detalle_muestraElIncidente', async () => {
    renderizarRuta('/ordenes/1')

    const incidente = await screen.findByRole('region', { name: 'Incidente' })
    expect(within(incidente).getByText('INC-0001')).toBeInTheDocument()
    expect(within(incidente).getByText(ETIQUETA_ORIGEN.RECLAMO_ORGANISMO)).toBeInTheDocument()
    expect(within(incidente).getByText('Lámpara quemada')).toBeInTheDocument()
    expect(within(incidente).getByText(ETIQUETA_PRIORIDAD.MEDIA)).toBeInTheDocument()
    // Sin tercero ni causa, no se muestran.
    expect(within(incidente).queryByText('Tercero')).not.toBeInTheDocument()
    expect(within(incidente).queryByText('Causa')).not.toBeInTheDocument()
  })

  it('historial_muestraQuienPausoYReanudoConGuionSiSigueAbierta', async () => {
    renderizarRuta('/ordenes/2')

    expect(await screen.findByRole('columnheader', { name: 'Pausó' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Reanudó' })).toBeInTheDocument()
    const celdas = within(screen.getByRole('row', { name: /UTE/ })).getAllByRole('cell')
    expect(celdas[4]).toHaveTextContent('Operador de Prueba')
    expect(celdas[5]).toHaveTextContent('—')
  })

  it('reanudar_muestraQuienReanudo', async () => {
    // Reanuda alguien distinto de quien pausó, para distinguir los nombres.
    base.idSesion = ID_TECNICO_NORTE
    renderizarRuta('/ordenes/2')

    fireEvent.click(await screen.findByRole('button', { name: 'Reanudar' }))

    await screen.findByText('Asignada', { selector: 'dd' })
    const celdas = within(screen.getByRole('row', { name: /UTE/ })).getAllByRole('cell')
    expect(celdas[4]).toHaveTextContent('Operador de Prueba')
    expect(celdas[5]).toHaveTextContent('Técnico de Prueba')
  })
})

describe('fotos de la intervención en el detalle (#90)', () => {
  it('lasMuestraAgrupadasPorMomento', async () => {
    base.idSesion = ID_OPERADOR
    const subidaEn = new Date().toISOString()
    base.fotosCierre.push(
      {
        id: 1,
        idOrdenTrabajo: 1,
        momento: 'ANTES',
        nombreOriginal: 'antes.jpg',
        tipo: 'image/jpeg',
        tamanioBytes: 10,
        subidaEn,
      },
      {
        id: 2,
        idOrdenTrabajo: 1,
        momento: 'DESPUES',
        nombreOriginal: 'despues.jpg',
        tipo: 'image/jpeg',
        tamanioBytes: 10,
        subidaEn,
      },
    )

    renderizarRuta('/ordenes/1')

    expect(
      await screen.findByRole('heading', { name: 'Fotos de la intervención' }),
    ).toBeInTheDocument()
    const antes = screen.getByRole('list', { name: 'Fotos: Antes' })
    expect(within(antes).getByRole('img', { name: 'antes.jpg' })).toHaveAttribute(
      'src',
      '/api/v1/ordenes/1/fotos/1',
    )
    expect(screen.getByRole('list', { name: 'Fotos: Después' })).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Fotos: Durante' })).not.toBeInTheDocument()
  })
})

describe('fotos del incidente en el detalle de la OT (#85)', () => {
  const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4])

  function agregarFoto(id: number) {
    base.fotos.push({
      id,
      idIncidente: orden(1).idIncidente!,
      nombreOriginal: `foto-${id}.jpg`,
      tipo: 'image/jpeg',
      tamanioBytes: JPEG.length,
      subidaEn: new Date().toISOString(),
    })
  }

  it('tecnicoDeLaCuadrilla_veLasFotosPeroNoPuedeAgregar', async () => {
    base.idSesion = ID_TECNICO_NORTE
    agregarFoto(1)

    renderizarRuta('/ordenes/1')

    const lista = await screen.findByRole('list', { name: 'Fotos del incidente' })
    expect(within(lista).getByRole('img', { name: 'foto-1.jpg' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Agregar fotos')).not.toBeInTheDocument()
  })

  it('operador_agregaUnaFotoDespuesDelRegistro', async () => {
    base.idSesion = ID_OPERADOR
    renderizarRuta('/ordenes/1')

    const selector = await screen.findByLabelText('Agregar fotos')
    expect(screen.getByText('El incidente no tiene fotos.')).toBeInTheDocument()
    fireEvent.change(selector, {
      target: { files: [new File([JPEG], 'poste.jpg', { type: 'image/jpeg' })] },
    })

    expect(await screen.findByText('Se adjuntaron 1 foto.')).toBeInTheDocument()
    expect(await screen.findByRole('img', { name: 'poste.jpg' })).toBeInTheDocument()
    expect(base.fotos.filter((f) => f.idIncidente === orden(1).idIncidente)).toHaveLength(1)
  })

  it('conTresFotos_noPermiteAgregarMas', async () => {
    base.idSesion = ID_OPERADOR
    agregarFoto(1)
    agregarFoto(2)
    agregarFoto(3)

    renderizarRuta('/ordenes/1')

    expect(await screen.findByLabelText('Agregar fotos')).toBeDisabled()
    expect(screen.getByText('El incidente ya tiene 3 fotos, el máximo.')).toBeInTheDocument()
  })
})
