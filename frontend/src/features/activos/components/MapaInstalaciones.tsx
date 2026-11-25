import { useEffect } from 'react'
import type { LatLngBoundsExpression, LatLngTuple } from 'leaflet'
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { etiquetaInstalacion } from '../etiquetas'
import type { Instalacion } from '../types'
import './MapaInstalaciones.css'

/** Montevideo, mientras no llegan las instalaciones. */
const CENTRO_INICIAL: LatLngTuple = [-34.88, -56.17]
const ZOOM_INICIAL = 11
/** Al elegir una instalación se acerca hasta ver el cruce y sus calles. */
const ZOOM_ELEGIDA = 16

// Teselas de OpenStreetMap sin subdominios, como recomienda OSM. El proveedor para producción
// se define en HU-11 (política de uso de OSM).
const TESELAS = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

type Props = {
  /** Las que tienen coordenadas: las demás no se pueden ubicar, pero sí buscar. */
  instalaciones: Instalacion[]
  elegida: Instalacion | null
  /** Sin él, el mapa solo muestra (ficha de la instalación, HU-10). */
  onElegir?: (instalacion: Instalacion) => void
}

function coordenadas(instalacion: Instalacion): LatLngTuple {
  return [instalacion.latitud, instalacion.longitud]
}

/** El rectángulo que contiene a todas, sin depender de L.latLngBounds. */
function limites(instalaciones: Instalacion[]): LatLngBoundsExpression {
  const latitudes = instalaciones.map((i) => i.latitud)
  const longitudes = instalaciones.map((i) => i.longitud)
  return [
    [Math.min(...latitudes), Math.min(...longitudes)],
    [Math.max(...latitudes), Math.max(...longitudes)],
  ]
}

/** Centra en la elegida o, si no hay, encuadra todas las instalaciones. */
function Encuadre({ instalaciones, elegida }: Omit<Props, 'onElegir'>) {
  const map = useMap()

  // La grilla del paso cambia el tamaño del contenedor después de que Leaflet lo midió: sin
  // esto quedan zonas grises sin teselas.
  useEffect(() => {
    map.invalidateSize()
  }, [map])

  const idElegida = elegida?.id
  const cantidad = instalaciones.length
  useEffect(() => {
    if (elegida) {
      map.setView(coordenadas(elegida), ZOOM_ELEGIDA)
    } else if (cantidad > 0) {
      map.fitBounds(limites(instalaciones), { padding: [24, 24], maxZoom: ZOOM_ELEGIDA })
    }
    // Solo al cambiar la elegida o al llegar la lista, no en cada render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, idElegida, cantidad])

  return null
}

/**
 * Mapa plano con las instalaciones como marcadores (HU-11 parcial, #70). Clic en un marcador la
 * elige; la elegida se resalta y el mapa se centra en ella.
 */
export function MapaInstalaciones({ instalaciones, elegida, onElegir }: Props) {
  // La elegida se dibuja al final para que quede encima de las vecinas.
  const ordenadas = elegida
    ? [
        ...instalaciones.filter((i) => i.id !== elegida.id),
        ...instalaciones.filter((i) => i.id === elegida.id),
      ]
    : instalaciones

  return (
    <div className="mapa-instalaciones" role="region" aria-label="Mapa de instalaciones">
      <MapContainer center={CENTRO_INICIAL} zoom={ZOOM_INICIAL} className="mapa-instalaciones-mapa">
        <TileLayer url={TESELAS} attribution={ATRIBUCION} />
        {ordenadas.map((instalacion) => {
          const esElegida = instalacion.id === elegida?.id
          return (
            // Leaflet no actualiza la clase de un marcador ya creado: al cambiar de elegida se
            // vuelve a crear.
            <CircleMarker
              key={`${instalacion.id}-${esElegida}`}
              center={coordenadas(instalacion)}
              radius={esElegida ? 10 : 6}
              pathOptions={{
                className: esElegida ? 'mapa-marcador mapa-marcador-elegido' : 'mapa-marcador',
              }}
              eventHandlers={{ click: () => onElegir?.(instalacion) }}
            >
              <Tooltip>{etiquetaInstalacion(instalacion)}</Tooltip>
            </CircleMarker>
          )
        })}
        <Encuadre instalaciones={instalaciones} elegida={elegida} />
      </MapContainer>
    </div>
  )
}
