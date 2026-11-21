import type { Instalacion } from './types'

/** «INST-001 — Cruce Ficticio 1»: cómo se muestra una instalación elegida. */
export function etiquetaInstalacion(instalacion: Pick<Instalacion, 'codigo' | 'nombre'>) {
  return `${instalacion.codigo} — ${instalacion.nombre}`
}
