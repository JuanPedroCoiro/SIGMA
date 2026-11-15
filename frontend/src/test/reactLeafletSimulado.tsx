/* eslint-disable react-refresh/only-export-components -- simula un módulo con hooks, no es código de la app. */
import type { ReactNode } from 'react'
import { vi } from 'vitest'

// react-leaflet simulado para jsdom (#70): sin Leaflet real ni teselas. Lo registra setup.ts
// para todos los tests. Cada marcador es un botón con el texto de su Tooltip y la clase de su
// pathOptions, así los tests lo pueden pulsar y ver si está resaltado.

/** Lo que el mapa le pide a Leaflet; se limpia después de cada test. */
export const mapaSimulado = {
  setView: vi.fn(),
  fitBounds: vi.fn(),
  invalidateSize: vi.fn(),
}

export function MapContainer({
  children,
  className,
}: {
  children?: ReactNode
  className?: string
}) {
  return <div className={className}>{children}</div>
}

export function TileLayer() {
  return null
}

export function CircleMarker({
  children,
  pathOptions,
  eventHandlers,
}: {
  children?: ReactNode
  pathOptions?: { className?: string }
  eventHandlers?: { click?: () => void }
}) {
  return (
    <button type="button" className={pathOptions?.className} onClick={eventHandlers?.click}>
      {children}
    </button>
  )
}

export function Tooltip({ children }: { children?: ReactNode }) {
  return <>{children}</>
}

export function useMap() {
  return mapaSimulado
}
