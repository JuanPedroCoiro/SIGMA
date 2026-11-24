import { fireEvent, screen, waitFor, within } from '@testing-library/react'
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

function orden(id: number) {
  return base.ordenes.find((o) => o.id === id)!
}

function jpg(nombre: string) {
  return new File([new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 1, 2])], nombre, {
    type: 'image/jpeg',
  })
}

/** Abre la intervención de OT-0001 ya En proceso, con el formulario de cierre a la vista. */
async function abrirCierre() {
  orden(1).estado = 'EN_PROCESO'
  orden(1).iniciadaEn = new Date().toISOString()
  renderizarRuta('/mis-ots/1')
  return within(await screen.findByRole('form', { name: 'Cierre técnico' }))
}

/** Completa diagnóstico y solución y agrega dos fotos de «Después». */
async function completarCierre(formulario: Awaited<ReturnType<typeof abrirCierre>>) {
  fireEvent.change(formulario.getByLabelText('Diagnóstico'), {
    target: { value: 'Lámpara roja quemada' },
  })
  fireEvent.change(formulario.getByLabelText('Solución'), {
    target: { value: 'Se cambió la lámpara' },
  })
  fireEvent.change(formulario.getByLabelText('Momento de las próximas fotos'), {
    target: { value: 'DESPUES' },
  })
  fireEvent.change(formulario.getByLabelText('Sacar o elegir fotos'), {
    target: { files: [jpg('poste.jpg'), jpg('lampara.jpg')] },
  })
  await formulario.findByRole('img', { name: 'lampara.jpg' })
}

function confirmar() {
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar cierre técnico' }))
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

    it('desdeLaIntervencion_muestraElFormularioDeCierre', async () => {
      renderizarRuta('/mis-ots/1')
      expect(await screen.findByRole('heading', { name: 'OT-0001' })).toBeVisible()
      expect(screen.getByText('Lámpara quemada (prioridad media)')).toBeVisible()
      expect(screen.queryByRole('form', { name: 'Cierre técnico' })).not.toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Iniciar intervención de OT-0001' }))

      expect(await screen.findByRole('form', { name: 'Cierre técnico' })).toBeVisible()
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

  describe('cierre técnico', () => {
    it('sinCamposNiFotos_losMarcaYNoEnvia', async () => {
      const formulario = await abrirCierre()

      confirmar()

      expect(await formulario.findByText('Indicá el diagnóstico.')).toBeVisible()
      expect(formulario.getByText('Indicá la solución.')).toBeVisible()
      expect(formulario.getByText('Agregá al menos una foto.')).toBeVisible()
      expect(formulario.getByLabelText('Diagnóstico')).toHaveAttribute('aria-invalid', 'true')
      expect(formulario.getByLabelText('Solución')).toHaveAttribute('aria-invalid', 'true')
      expect(formulario.getByLabelText('Observaciones')).not.toHaveAttribute('aria-invalid')
      expect(orden(1).estado).toBe('EN_PROCESO')
    })

    it('conCamposSinFotos_pideLaFoto', async () => {
      const formulario = await abrirCierre()
      fireEvent.change(formulario.getByLabelText('Diagnóstico'), { target: { value: 'Algo' } })
      fireEvent.change(formulario.getByLabelText('Solución'), { target: { value: 'Algo' } })

      confirmar()

      expect(await formulario.findByText('Agregá al menos una foto.')).toBeVisible()
      expect(orden(1).estado).toBe('EN_PROCESO')
    })

    it('conFotos_quedaEnviadaPendienteDeValidacion', async () => {
      const formulario = await abrirCierre()
      await completarCierre(formulario)
      fireEvent.change(formulario.getByLabelText('Observaciones'), {
        target: { value: 'Quedó funcionando' },
      })

      confirmar()

      const aviso = await screen.findByText('Enviada, pendiente de validación.')
      // Las fotos del cierre se vuelven a pedir: la consulta [CLAVE_FOTOS_ORDEN, id] se invalidó.
      await waitFor(() =>
        expect(aviso.closest('[role="status"]')).toHaveTextContent('Se adjuntaron 2 fotos.'),
      )
      expect(orden(1).estado).toBe('CERRADA_TECNICA')
      expect(orden(1).cierre).toMatchObject({
        diagnostico: 'Lámpara roja quemada',
        solucion: 'Se cambió la lámpara',
        observaciones: 'Quedó funcionando',
      })
      expect(base.fotosCierre).toHaveLength(2)
      expect(base.fotosCierre.map((f) => [f.nombreOriginal, f.momento])).toEqual([
        ['poste.jpg', 'DESPUES'],
        ['lampara.jpg', 'DESPUES'],
      ])
      // Las vistas previas se liberan después de cerrar.
      expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2)
    })

    it('sinConexion_conservaElFormularioYElReintentoUsaElMismoIdOperacion', async () => {
      const ids: string[] = []
      servidor.use(
        http.post(URL_CERRAR, async ({ request }) => {
          ids.push(await idOperacionDe(request))
          // El primero no llega; el segundo sigue al handler de la API simulada.
          if (ids.length === 1) return HttpResponse.error()
        }),
      )
      const formulario = await abrirCierre()
      await completarCierre(formulario)

      confirmar()

      expect(await formulario.findByRole('alert')).toHaveTextContent('No hay conexión')
      expect(formulario.getByLabelText('Diagnóstico')).toHaveValue('Lámpara roja quemada')
      expect(formulario.getAllByRole('img')).toHaveLength(2)
      expect(orden(1).estado).toBe('EN_PROCESO')

      fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

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
            diagnostico: 'Lámpara roja quemada',
            solucion: 'Se cambió la lámpara',
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
      const formulario = await abrirCierre()
      await completarCierre(formulario)
      confirmar()
      await formulario.findByRole('alert')

      fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

      expect(await screen.findByText('Enviada, pendiente de validación.')).toBeVisible()
      expect(intentos).toBe(2)
      expect(base.fotosCierre).toHaveLength(1)
    })

    it('reasignada_avisaYNoOfreceReintentar', async () => {
      const formulario = await abrirCierre()
      await completarCierre(formulario)
      orden(1).idCuadrilla = 2

      confirmar()

      const alerta = await formulario.findByRole('alert')
      expect(alerta).toHaveTextContent('Esta OT ya no es de tu cuadrilla')
      expect(within(alerta).getByRole('link', { name: 'Volver a Mis OTs' })).toBeVisible()
      expect(screen.queryByRole('button', { name: /Confirmar|Reintentar/ })).not.toBeInTheDocument()
    })

    it('yaCerrada_loAvisa', async () => {
      const formulario = await abrirCierre()
      await completarCierre(formulario)
      orden(1).estado = 'CERRADA_TECNICA'

      confirmar()

      expect(await formulario.findByRole('alert')).toHaveTextContent('Esta OT ya estaba cerrada')
      expect(base.fotosCierre).toHaveLength(0)
    })

    it('anulada_loAvisa', async () => {
      const formulario = await abrirCierre()
      await completarCierre(formulario)
      orden(1).estado = 'ANULADA'

      confirmar()

      expect(await formulario.findByRole('alert')).toHaveTextContent('Esta OT fue anulada')
    })

    it('fotoInvalida_muestraElMensajeJuntoALasFotosYDejaReintentar', async () => {
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
      const formulario = await abrirCierre()
      await completarCierre(formulario)

      confirmar()

      expect(await formulario.findByRole('alert')).toHaveTextContent(
        'Indicá el momento de cada foto',
      )
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Confirmar cierre técnico' })).toBeEnabled(),
      )
      expect(orden(1).estado).toBe('EN_PROCESO')
    })
  })
})
