import { isAxiosError } from 'axios'
import { obtenerProblemDetail, tipoProblema, TIPOS_PROBLEMA } from '../../shared/api/problemDetail'
import { formatearHoraMontevideo } from '../../shared/lib/fechas'

/** Mensaje para un login fallido (CU-12, 4a a 4c). */
export function mensajeErrorInicioSesion(error: unknown): string {
  if (!isAxiosError(error) || !error.response) {
    return 'No se pudo conectar con el servidor. Revisá la conexión y volvé a intentar.'
  }
  const problema = obtenerProblemDetail(error)
  const status = error.response.status

  if (status === 423) {
    const hasta = problema?.bloqueadoHasta
    return typeof hasta === 'string'
      ? `Tu cuenta está bloqueada por varios intentos fallidos. Podés volver a intentar a las ${formatearHoraMontevideo(hasta)}.`
      : 'Tu cuenta está bloqueada temporalmente por varios intentos fallidos.'
  }
  if (status === 401 && tipoProblema(problema) === TIPOS_PROBLEMA.cuentaDesactivada) {
    return 'Tu cuenta está desactivada. Contactá a la Jefatura para reactivarla.'
  }
  if (status === 401) {
    return 'Correo o contraseña incorrectos.'
  }
  if (status === 403) {
    return 'La sesión de seguridad no es válida. Recargá la página y volvé a intentar.'
  }
  return 'Ocurrió un error inesperado. Volvé a intentar en unos minutos.'
}
