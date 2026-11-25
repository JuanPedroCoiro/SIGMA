import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw/http'
import { beforeEach, describe, expect, it } from 'vitest'
import { base } from '../../mocks/datos'
import { partesDelMultipart } from '../../mocks/handlers/fotos'
import { problema } from '../../mocks/problemas'
import { servidor } from '../../mocks/servidor'
import { TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { renderizarRuta } from '../../test/renderizarRuta'

// Datos de la API simulada (src/mocks/datos.ts): el Técnico de Prueba es de Cuadrilla Norte;
// OT-0001 está asignada a Norte para hoy, de INC-0001 («Lámpara quemada»).
const ID_TECNICO_NORTE = 2
const URL_CERRAR = '/api/v1/ordenes/:id/cerrar'
const DIAGNOSTICO = 'Lámpara roja quemada'
const SOLUCION = 'Se cambió la lámpara'

function orden(id: number) {
  return base.ordenes.find((o) => o.id === id)!
}

function jpg(nombre: string) {
  return new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2])], nombre, {
    type: 'image/jpeg',
  })
}

function pulsar(nombre: string) {
  fireEvent.click(screen.getByRole('button', { name: nombre }))
}

/** Espera el paso del cierre por su título («Trabajo», «Fotos», «Revisar y enviar»). */
function enPaso(titulo: string) {
  return screen.findByRole('heading', { level: 3, name: titulo })
}

/** Abre la intervención de OT-0001 ya En proceso, con el cierre en el paso 1. */
async function abrirCierre(ruta = '/mis-ots/1') {
  orden(1).estado = 'EN_PROCESO'
  orden(1).iniciadaEn = new Date().toISOString()
  const { router } = renderizarRuta(ruta)
  await screen.findByRole('form', { name: 'Cierre técnico' })
  return router
}

/** Paso 1: diagnóstico y solución. */
function completarTrabajo() {
  fireEvent.change(screen.getByLabelText('Diagnóstico'), { target: { value: DIAGNOSTICO } })
  fireEvent.change(screen.getByLabelText('Solución'), { target: { value: SOLUCION } })
}

/** Paso 2: dos fotos de «Después». */
async function completarFotos() {
  fireEvent.change(screen.getByLabelText('Momento de las próximas fotos'), {
    target: { value: 'DESPUES' },
  })
  fireEvent.change(screen.getByLabelText('Sacar o elegir fotos'), {
    target: { files: [jpg('poste.jpg'), jpg('lampara.jpg')] },
  })
  await screen.findByRole('img', { name: 'lampara.jpg' })
}

/** Completa los dos primeros pasos y llega a «Revisar y enviar». */
async function irAlResumen() {
  completarTrabajo()
  pulsar('Siguiente')
  await enPaso('Fotos')
  await completarFotos()
  pulsar('Siguiente')
  await enPaso('Revisar y enviar')
}

function confirmar() {
  pulsar('Confirmar cierre técnico')
}

/** El idOperacion que mandó un pedido de cierre, sin consumir su cuerpo. */
async function idOperacionDe(request: Request) {
  const partes = await partesDelMultipart(request.clone())
  const datos = partes.find((p) => p.campo === 'datos')!
  return (JSON.parse(new TextDecoder().decode(datos.bytes)) as { idOperacion: string }).idOperacion
}

describe('intervención del técnico (HU-07)', () => {
  beforeEach(() => {
    base.idSesion = ID_TECNICO_NORTE
  })

  describe('iniciar', () => {
    it('desdeLaTarjeta_quedaEnProceso', async () => {
      renderizarRuta('/mis-ots')
      const tarjeta = within(await screen.findByRole('listitem', { name: 'OT-0001' }))

      fireEvent.click(tarjeta.getByRole('button', { name: 'Iniciar intervención de OT-0001' }))

      expect(await tarjeta.findByText('En proceso')).toBeVisible()
      expect(tarjeta.queryByRole('button', { name: /Iniciar/ })).not.toBeInTheDocument()
      expect(orden(1).estado).toBe('EN_PROCESO')
      expect(orden(1).iniciadaEn).not.toBeNull()
    })

    it('desdeLaIntervencion_muestraElCierreEnElPrimerPaso', async () => {
      renderizarRuta('/mis-ots/1')
      expect(await screen.findByRole('heading', { name: 'OT-0001' })).toBeVisible()
      expect(screen.getByText('Lámpara quemada (prioridad media)')).toBeVisible()
      expect(screen.queryByRole('form', { name: 'Cierre técnico' })).not.toBeInTheDocument()

      pulsar('Iniciar intervención de OT-0001')

      expect(await screen.findByRole('form', { name: 'Cierre técnico' })).toBeVisible()
      expect(await enPaso('Trabajo')).toBeVisible()
      expect(screen.getByText('Paso 1 de 3')).toBeVisible()
      expect(screen.getByText('Iniciada')).toBeVisible()
      expect(screen.getByRole('button', { name: 'Pausar' })).toBeVisible()
    })

    it('reintentoYaEnProceso_loTomaComoExito', async () => {
      // El primer intento llegó, pero se perdió la respuesta: el reintento recibe 400 EN_PROCESO.
      servidor.use(
        http.post('/api/v1/ordenes/:id/iniciar', () => {
          orden(1).estado = 'EN_PROCESO'
          return problema(
            400,
            TIPOS_PROBLEMA.transicionInvalida,
            'Regla de negocio no cumplida',
            'No se puede iniciar una OT en estado EN_PROCESO.',
            { estadoActual: 'EN_PROCESO' },
          )
        }),
      )
      renderizarRuta('/mis-ots/1')

      fireEvent.click(
        await screen.findByRole('button', { name: 'Iniciar intervención de OT-0001' }),
      )

      expect(await screen.findByRole('form', { name: 'Cierre técnico' })).toBeVisible()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('reasignada_avisaQueYaNoEsDeSuCuadrilla', async () => {
      renderizarRuta('/mis-ots/1')
      const boton = await screen.findByRole('button', { name: 'Iniciar intervención de OT-0001' })
      orden(1).idCuadrilla = 2

      fireEvent.click(boton)

      expect(await screen.findByRole('alert')).toHaveTextContent('ya no es de tu cuadrilla')
      expect(orden(1).estado).toBe('ASIGNADA')
    })
  })

  describe('cierre en pasos: navegación', () => {
    it('trabajoSinCompletar_noAvanzaMarcaLosCamposYSeCorrigeAlEscribir', async () => {
      await abrirCierre()

      pulsar('Siguiente')

      expect(await screen.findByText('Indicá el diagnóstico.')).toBeVisible()
      expect(screen.getByText('Indicá la solución.')).toBeVisible()
      expect(screen.getByLabelText('Diagnóstico')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByLabelText('Solución')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByLabelText('Observaciones')).not.toHaveAttribute('aria-invalid')
      expect(await enPaso('Trabajo')).toBeVisible()

      fireEvent.change(screen.getByLabelText('Diagnóstico'), { target: { value: DIAGNOSTICO } })

      await waitFor(() =>
        expect(screen.queryByText('Indicá el diagnóstico.')).not.toBeInTheDocument(),
      )
      expect(screen.getByText('Indicá la solución.')).toBeVisible()
    })

    it('fotosSinNinguna_noAvanza', async () => {
      await abrirCierre()
      completarTrabajo()
      pulsar('Siguiente')
      await enPaso('Fotos')

      pulsar('Siguiente')

      expect(await screen.findByText('Agregá al menos una foto.')).toBeVisible()
      expect(await enPaso('Fotos')).toBeVisible()

      await completarFotos()
      expect(screen.queryByText('Agregá al menos una foto.')).not.toBeInTheDocument()
    })

    it('anterior_noPierdeLoCargado', async () => {
      await abrirCierre()
      await irAlResumen()

      pulsar('Anterior')
      await enPaso('Fotos')
      expect(screen.getAllByRole('img')).toHaveLength(2)

      pulsar('Anterior')
      await enPaso('Trabajo')
      expect(screen.getByLabelText('Diagnóstico')).toHaveValue(DIAGNOSTICO)
      expect(screen.getByLabelText('Solución')).toHaveValue(SOLUCION)
    })

    it('atrasDelNavegador_vuelveAlPasoAnteriorConLosDatos', async () => {
      const router = await abrirCierre()
      await irAlResumen()
      expect(router.state.location.search).toBe('?paso=3')

      await act(() => router.navigate(-1))
      await enPaso('Fotos')
      expect(router.state.location.search).toBe('?paso=2')
      expect(screen.getAllByRole('img')).toHaveLength(2)

      await act(() => router.navigate(-1))
      await enPaso('Trabajo')
      expect(screen.getByLabelText('Diagnóstico')).toHaveValue(DIAGNOSTICO)
    })

    it('pasoSinCompletarLosAnteriores_vuelveAlPrimero', async () => {
      const router = await abrirCierre('/mis-ots/1?paso=3')

      expect(await enPaso('Trabajo')).toBeVisible()
      await waitFor(() => expect(router.state.location.search).toBe(''))
    })

    it('resumen_muestraLoCargadoConLasFotosPorMomentoYSeEditaPorSeccion', async () => {
      await abrirCierre()
      completarTrabajo()
      pulsar('Siguiente')
      await enPaso('Fotos')
      await completarFotos()
      // Una foto más, de «Antes».
      fireEvent.change(screen.getByLabelText('Momento de las próximas fotos'), {
        target: { value: 'ANTES' },
      })
      fireEvent.change(screen.getByLabelText('Sacar o elegir fotos'), {
        target: { files: [jpg('llegada.jpg')] },
      })
      await screen.findByRole('img', { name: 'llegada.jpg' })
      pulsar('Siguiente')
      await enPaso('Revisar y enviar')

      const trabajo = within(screen.getByRole('region', { name: 'Trabajo' }))
      expect(trabajo.getByText(DIAGNOSTICO)).toBeVisible()
      expect(trabajo.getByText(SOLUCION)).toBeVisible()
      expect(trabajo.getByText('Sin observaciones')).toBeVisible()
      const fotos = within(screen.getByRole('region', { name: 'Fotos' }))
      expect(
        within(fotos.getByRole('list', { name: 'Fotos: Antes' })).getAllByRole('img'),
      ).toHaveLength(1)
      expect(
        within(fotos.getByRole('list', { name: 'Fotos: Después' })).getAllByRole('img'),
      ).toHaveLength(2)
      expect(fotos.queryByRole('list', { name: 'Fotos: Durante' })).not.toBeInTheDocument()

      pulsar('Editar Trabajo')
      await enPaso('Trabajo')
      fireEvent.change(screen.getByLabelText('Solución'), {
        target: { value: 'Se cambió la lámpara y el portalámpara' },
      })
      pulsar('Volver al resumen')

      await enPaso('Revisar y enviar')
      expect(screen.getByText('Se cambió la lámpara y el portalámpara')).toBeVisible()
    })

    it('salirSinCerrar_liberaLasVistasPrevias', async () => {
      await abrirCierre()
      completarTrabajo()
      pulsar('Siguiente')
      await enPaso('Fotos')
      await completarFotos()

      fireEvent.click(screen.getByRole('link', { name: '← Mis OTs' }))

      expect(await screen.findByRole('heading', { name: 'Mis OTs' })).toBeVisible()
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
    })
  })

  describe('cierre en pasos: envío', () => {
    it('confirmar_quedaEnviadaPendienteDeValidacion', async () => {
      const router = await abrirCierre()
      completarTrabajo()
      fireEvent.change(screen.getByLabelText('Observaciones'), {
        target: { value: 'Quedó funcionando' },
      })
      pulsar('Siguiente')
      await enPaso('Fotos')
      await completarFotos()
      pulsar('Siguiente')
      await enPaso('Revisar y enviar')

      confirmar()

      const aviso = await screen.findByText('Enviada, pendiente de validación.')
      // Las fotos del cierre se vuelven a pedir: la consulta [CLAVE_FOTOS_ORDEN, id] se invalidó.
      await waitFor(() =>
        expect(aviso.closest('[role="status"]')).toHaveTextContent('Se adjuntaron 2 fotos.'),
      )
      expect(orden(1).estado).toBe('CERRADA_TECNICA')
      expect(orden(1).cierre).toMatchObject({
        diagnostico: DIAGNOSTICO,
        solucion: SOLUCION,
        observaciones: 'Quedó funcionando',
      })
      expect(base.fotosCierre.map((f) => [f.nombreOriginal, f.momento])).toEqual([
        ['poste.jpg', 'DESPUES'],
        ['lampara.jpg', 'DESPUES'],
      ])
      // Las vistas previas se liberan después de cerrar y el paso sale de la URL.
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
      await waitFor(() => expect(router.state.location.search).toBe(''))
    })

    it('sinConexion_quedaEnElResumenYElReintentoUsaElMismoIdOperacion', async () => {
      const ids: string[] = []
      servidor.use(
        http.post(URL_CERRAR, async ({ request }) => {
          ids.push(await idOperacionDe(request))
          // El primero no llega; el segundo sigue al handler de la API simulada.
          if (ids.length === 1) return HttpResponse.error()
        }),
      )
      await abrirCierre()
      await irAlResumen()

      confirmar()

      expect(await screen.findByRole('alert')).toHaveTextContent('No hay conexión')
      expect(await enPaso('Revisar y enviar')).toBeVisible()
      expect(screen.getAllByRole('img')).toHaveLength(2)
      expect(orden(1).estado).toBe('EN_PROCESO')

      pulsar('Reintentar')

      expect(await screen.findByText('Enviada, pendiente de validación.')).toBeVisible()
      expect(ids).toHaveLength(2)
      expect(ids[1]).toBe(ids[0])
      expect(base.fotosCierre).toHaveLength(2)
    })

    it('respuestaPerdida_elReintentoNoCierraDosVecesNiDuplicaFotos', async () => {
      // El primer envío se procesó pero la respuesta no llegó: el reintento lleva el mismo id.
      let intentos = 0
      servidor.use(
        http.post(URL_CERRAR, async ({ request }) => {
          intentos++
          if (intentos > 1) return
          const id = await idOperacionDe(request)
          orden(1).estado = 'CERRADA_TECNICA'
          orden(1).cierre = {
            idOperacion: id,
            diagnostico: DIAGNOSTICO,
            solucion: SOLUCION,
            observaciones: null,
            cerradaEn: new Date().toISOString(),
          }
          base.fotosCierre.push({
            id: 1,
            idOrdenTrabajo: 1,
            nombreOriginal: 'poste.jpg',
            tipo: 'image/jpeg',
            tamanioBytes: 6,
            subidaEn: new Date().toISOString(),
            momento: 'DESPUES',
          })
          return HttpResponse.error()
        }),
      )
      await abrirCierre()
      await irAlResumen()
      confirmar()
      await screen.findByRole('alert')

      pulsar('Reintentar')

      expect(await screen.findByText('Enviada, pendiente de validación.')).toBeVisible()
      expect(intentos).toBe(2)
      expect(base.fotosCierre).toHaveLength(1)
    })

    it('errorDeValidacionDelServidor_llevaAlPasoDelCampo', async () => {
      servidor.use(
        http.post(URL_CERRAR, () =>
          problema(400, TIPOS_PROBLEMA.validacion, 'Datos inválidos', 'Revisá los campos.', {
            errores: [
              { campo: 'diagnostico', mensaje: 'El diagnóstico admite hasta 2000 caracteres.' },
            ],
          }),
        ),
      )
      await abrirCierre()
      await irAlResumen()

      confirmar()

      expect(await enPaso('Trabajo')).toBeVisible()
      expect(screen.getByRole('alert')).toHaveTextContent('Revisá los datos marcados.')
      expect(screen.getByText('El diagnóstico admite hasta 2000 caracteres.')).toBeVisible()
      expect(screen.getByLabelText('Diagnóstico')).toHaveAttribute('aria-invalid', 'true')
      await waitFor(() => expect(screen.getByLabelText('Diagnóstico')).toHaveFocus())
      expect(screen.getByRole('button', { name: 'Volver al resumen' })).toBeVisible()
    })

    it('fotoInvalida_llevaAlPasoDeLasFotosConElMensaje', async () => {
      servidor.use(
        http.post(URL_CERRAR, () =>
          problema(
            400,
            TIPOS_PROBLEMA.fotoInvalida,
            'Regla de negocio no cumplida',
            'Indicá el momento de cada foto: antes, durante o después del trabajo.',
          ),
        ),
      )
      await abrirCierre()
      await irAlResumen()

      confirmar()

      expect(await enPaso('Fotos')).toBeVisible()
      expect(screen.getByRole('alert')).toHaveTextContent('Indicá el momento de cada foto')
      expect(screen.getAllByRole('img')).toHaveLength(2)
      expect(orden(1).estado).toBe('EN_PROCESO')
    })

    it('reasignada_avisaYNoOfreceReintentar', async () => {
      await abrirCierre()
      await irAlResumen()
      orden(1).idCuadrilla = 2

      confirmar()

      const alerta = await screen.findByRole('alert')
      expect(alerta).toHaveTextContent('Esta OT ya no es de tu cuadrilla')
      expect(within(alerta).getByRole('link', { name: 'Volver a Mis OTs' })).toBeVisible()
      expect(screen.queryByRole('button', { name: /Confirmar|Reintentar/ })).not.toBeInTheDocument()
    })

    it('yaCerrada_loAvisa', async () => {
      await abrirCierre()
      await irAlResumen()
      orden(1).estado = 'CERRADA_TECNICA'

      confirmar()

      expect(await screen.findByRole('alert')).toHaveTextContent('Esta OT ya estaba cerrada')
      expect(base.fotosCierre).toHaveLength(0)
    })

    it('anulada_loAvisa', async () => {
      await abrirCierre()
      await irAlResumen()
      orden(1).estado = 'ANULADA'

      confirmar()

      expect(await screen.findByRole('alert')).toHaveTextContent('Esta OT fue anulada')
    })
  })

  describe('pausar durante el cierre', () => {
    const AVISO = 'Vas a perder lo cargado del cierre. ¿Pausar igual?'

    it('sinNadaCargado_abreLaPausaDirecto', async () => {
      await abrirCierre()

      pulsar('Pausar')

      const dialogo = await screen.findByRole('dialog', { name: 'Pausar OT-0001' })
      expect(within(dialogo).getByLabelText('Motivo')).toBeVisible()
      expect(screen.queryByText(AVISO)).not.toBeInTheDocument()
    })

    it('conTextoCargado_pideConfirmacion', async () => {
      await abrirCierre()
      fireEvent.change(screen.getByLabelText('Diagnóstico'), { target: { value: DIAGNOSTICO } })

      pulsar('Pausar')
      const confirmacion = await screen.findByRole('dialog', { name: 'Pausar OT-0001' })
      expect(confirmacion).toHaveTextContent(AVISO)

      // Cancelar no cambia nada.
      fireEvent.click(within(confirmacion).getByRole('button', { name: 'Cancelar' }))
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(screen.getByLabelText('Diagnóstico')).toHaveValue(DIAGNOSTICO)
      expect(orden(1).estado).toBe('EN_PROCESO')

      // «Pausar igual» abre el diálogo de pausa de siempre.
      pulsar('Pausar')
      fireEvent.click(await screen.findByRole('button', { name: 'Pausar igual' }))
      const dialogo = await screen.findByRole('dialog', { name: 'Pausar OT-0001' })
      fireEvent.change(within(dialogo).getByLabelText('Motivo'), {
        target: { value: 'MATERIALES' },
      })
      fireEvent.click(within(dialogo).getByRole('button', { name: 'Confirmar pausa' }))

      expect(await screen.findByRole('button', { name: 'Reanudar' })).toBeVisible()
      expect(screen.getByRole('status')).toHaveTextContent('En pausa: Falta de materiales')
      expect(orden(1).estado).toBe('EN_PAUSA')
    })

    it('conSoloUnaFoto_tambienPideConfirmacion', async () => {
      await abrirCierre()
      completarTrabajo()
      pulsar('Siguiente')
      await enPaso('Fotos')
      fireEvent.change(screen.getByLabelText('Sacar o elegir fotos'), {
        target: { files: [jpg('poste.jpg')] },
      })
      await screen.findByRole('img', { name: 'poste.jpg' })
      // Vuelve al paso 1 y borra los textos: queda cargada solo la foto.
      pulsar('Anterior')
      await enPaso('Trabajo')
      fireEvent.change(screen.getByLabelText('Diagnóstico'), { target: { value: '' } })
      fireEvent.change(screen.getByLabelText('Solución'), { target: { value: '' } })

      pulsar('Pausar')

      expect(await screen.findByRole('dialog', { name: 'Pausar OT-0001' })).toHaveTextContent(AVISO)
    })
  })
})
