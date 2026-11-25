import { isAxiosError } from 'axios'
import { obtenerProblemDetail, tipoProblema, TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { ETIQUETA_ESTADO_OT, type EstadoOT } from './types'

/** Qué pasó al iniciar o cerrar una OT (CU-3 7b y 8b), para elegir qué ofrecer en la pantalla. */
export type TipoErrorEjecucion =
  | 'sin-conexion'
  | 'reasignada'
  | 'ya-cerrada'
  | 'anulada'
  | 'otro-estado'
  | 'repetida'
  | 'fotos'
  | 'otro'

export type ErrorEjecucion = { tipo: TipoErrorEjecucion; mensaje: string }

const ESTADOS_CERRADA: readonly EstadoOT[] = ['CERRADA_TECNICA', 'VALIDADA']

/**
 * El mensaje para el técnico según el error de iniciar o cerrar. Sin respuesta del servidor es
 * «sin conexión»: el formulario se conserva y se puede reintentar con el mismo idOperacion. Los
 * errores de validación por campo los pone el formulario con aplicarErroresDelServidor.
 */
export function mensajeErrorEjecucion(error: unknown): ErrorEjecucion {
  if (isAxiosError(error) && !error.response) {
    return {
      tipo: 'sin-conexion',
      mensaje:
        'No hay conexión. Lo que cargaste sigue acá: cuando tengas señal, tocá «Reintentar».',
    }
  }
  const problema = obtenerProblemDetail(error)
  const tipo = tipoProblema(problema)
  if (problema?.status === 403) {
    return {
      tipo: 'reasignada',
      mensaje: 'Esta OT ya no es de tu cuadrilla: la reasignaron. Consultá con la Jefatura.',
    }
  }
  if (tipo === TIPOS_PROBLEMA.transicionInvalida) {
    const estado = problema?.estadoActual as EstadoOT | undefined
    if (estado && ESTADOS_CERRADA.includes(estado)) {
      return { tipo: 'ya-cerrada', mensaje: 'Esta OT ya estaba cerrada: no hace falta enviarla.' }
    }
    if (estado === 'ANULADA') {
      return { tipo: 'anulada', mensaje: 'Esta OT fue anulada: ya no hay que trabajarla.' }
    }
    return {
      tipo: 'otro-estado',
      mensaje: estado
        ? `La OT está ${ETIQUETA_ESTADO_OT[estado].toLowerCase()}: recargá la pantalla.`
        : 'La OT cambió de estado: recargá la pantalla.',
    }
  }
  if (tipo === TIPOS_PROBLEMA.operacionRepetida) {
    return {
      tipo: 'repetida',
      mensaje: 'No se pudo confirmar el envío. Revisá los datos y volvé a confirmar el cierre.',
    }
  }
  if (tipo === TIPOS_PROBLEMA.fotoInvalida) {
    return { tipo: 'fotos', mensaje: problema?.detail ?? 'Revisá las fotos.' }
  }
  return {
    tipo: 'otro',
    mensaje: problema?.detail ?? 'No se pudo completar la operación. Volvé a intentar.',
  }
}
