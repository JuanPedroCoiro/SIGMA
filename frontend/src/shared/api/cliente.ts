import axios from 'axios'

// La sesión viaja en una cookie y Spring Security pide el token CSRF de la cookie
// XSRF-TOKEN en el header X-XSRF-TOKEN (los nombres por defecto de axios, explícitos acá).
export const cliente = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})
