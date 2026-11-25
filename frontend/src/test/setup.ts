import '@testing-library/jest-dom/vitest'
import { cleanup, screen } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { base, reiniciarBaseSimulada } from '../mocks/datos'
import { servidor } from '../mocks/servidor'
import { mapaSimulado } from './reactLeafletSimulado'
import { descartarQueryClients, renderizarRuta } from './renderizarRuta'

// Leaflet no funciona en jsdom (sin tamaños ni teselas): el mapa usa react-leaflet simulado (#70).
vi.mock('react-leaflet', () => import('./reactLeafletSimulado'))

// jsdom no implementa las URLs de objetos, que dan la vista previa de las fotos (#71).
let urlsCreadas = 0
URL.createObjectURL = vi.fn(() => `blob:vista-previa-${++urlsCreadas}`)
URL.revokeObjectURL = vi.fn()

const ID_JEFATURA = 1

/** Desmonta, espera los pedidos que quedaron en curso y recién entonces reinicia la base. */
async function limpiar() {
  cleanup()
  await descartarQueryClients()
  // React Query avisa a los componentes con un setTimeout (notifyManager). Si queda un aviso en
  // cola al terminar el último test del archivo, corre después de que Vitest desarma jsdom y React
  // falla con «window is not defined» fuera de cualquier test. Se deja correr acá, con jsdom vivo.
  await new Promise((resolver) => setTimeout(resolver, 0))
  servidor.resetHandlers()
  reiniciarBaseSimulada()
  Object.values(mapaSimulado).forEach((funcion) => funcion.mockClear())
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
