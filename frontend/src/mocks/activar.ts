/// <reference types="msw/vite/client" />
import { expirarSesionSimulada, reiniciarBaseSimulada } from './datos'
import { handlers } from './handlers'

declare global {
  interface Window {
    /** Atajos para probar a mano con la API simulada, desde la consola del navegador. */
    sigmaSimulacion?: {
      expirarSesion: () => void
      reiniciarDatos: () => void
    }
  }
}

/** Enciende la API simulada en el navegador. Solo la carga main.tsx en `npm run dev:simulado`. */
export async function activarApiSimulada() {
  const { network } = await import('virtual:msw')
  // Lo que no tiene handler (archivos de Vite, teselas del mapa) sigue de largo.
  network.configure({ handlers, onUnhandledFrame: 'bypass' })
  await network.enable()

  window.sigmaSimulacion = {
    expirarSesion: expirarSesionSimulada,
    reiniciarDatos: reiniciarBaseSimulada,
  }
  console.info(
    '[SIGMA] API simulada activa. Usuarios: jefatura@sigma.test, tecnico@sigma.test, ' +
      'operador@sigma.test, stock@sigma.test, bloqueado@sigma.test y desactivado@sigma.test; ' +
      'contraseña Prueba.1234. sigmaSimulacion.expirarSesion() vence la sesión.',
  )
}
