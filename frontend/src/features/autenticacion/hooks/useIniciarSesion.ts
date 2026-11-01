import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CLAVE_SESION, iniciarSesion } from '../api/sesionApi'

/**
 * Inicia sesión y vuelve a consultar «quién soy»: así se carga el usuario y llega el token CSRF
 * nuevo, que el backend renueva en el login.
 */
export function useIniciarSesion() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: iniciarSesion,
    onSuccess: () => queryClient.refetchQueries({ queryKey: CLAVE_SESION }),
  })
}
