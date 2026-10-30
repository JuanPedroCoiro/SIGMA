import axios, { isAxiosError } from 'axios'

// La sesión viaja en una cookie y Spring Security pide el token CSRF de la cookie
// XSRF-TOKEN en el header X-XSRF-TOKEN (los nombres por defecto de axios, explícitos acá).
export const cliente = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

/** La sesión maneja su propio 401: en «quién soy» es anónimo y en el login, credenciales. */
const RUTA_SESION = '/sesion'

let manejadorSesionExpirada: (() => void) | null = null

/** Lo registra el proveedor de la sesión, para que shared no dependa de app. */
export function registrarManejadorSesionExpirada(manejador: (() => void) | null) {
  manejadorSesionExpirada = manejador
}

// Un 401 en cualquier otro pedido significa que la sesión venció (CU-12 *a).
cliente.interceptors.response.use(undefined, (error: unknown) => {
  if (isAxiosError(error) && error.response?.status === 401 && error.config?.url !== RUTA_SESION) {
    manejadorSesionExpirada?.()
  }
  return Promise.reject(error)
})
