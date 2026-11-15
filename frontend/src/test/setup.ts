import '@testing-library/jest-dom/vitest'
import { cleanup, screen } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { base, reiniciarBaseSimulada } from '../mocks/datos'
import { servidor } from '../mocks/servidor'
import { descartarQueryClients, renderizarRuta } from './renderizarRuta'

const ID_JEFATURA = 1

/** Desmonta, espera los pedidos que quedaron en curso y recién entonces reinicia la base. */
async function limpiar() {
  cleanup()
  await descartarQueryClients()
  servidor.resetHandlers()
  reiniciarBaseSimulada()
}

/**
 * Calentamiento (#66). El primer render de la app en cada archivo de test, con su primer pedido
 * por MSW y su primera consulta por rol, tarda unas tres veces más que los siguientes. Con
 * varios archivos arrancando a la vez, ese costo hacía vencer el primer `findBy` del primer
 * test. Acá se paga una vez fuera de los tests y no se sube ningún timeout. Las páginas no se
 * cargan con lazy(): el router las importa al importar el test, así que no hace falta
 * importarlas acá.
 */
async function calentar() {
  try {
    base.idSesion = ID_JEFATURA
    renderizarRuta('/')
    await screen.findByRole('list', { name: 'Resumen' }, { timeout: 8000 })
    screen.getByRole('navigation', { name: 'Menú principal' })
  } catch (error) {
    throw new Error(
      'Falló el calentamiento de los tests (#66, src/test/setup.ts): la app no llegó a mostrar ' +
        'el inicio de la Jefatura. Revisá el error de abajo antes que los de los tests.',
      { cause: error },
    )
  } finally {
    await limpiar()
  }
}

// Los tests usan la misma API simulada que `npm run dev:simulado`. Un pedido sin handler falla.
beforeAll(async () => {
  servidor.listen({ onUnhandledFrame: 'error' })
  await calentar()
})

afterEach(limpiar)

afterAll(() => servidor.close())
