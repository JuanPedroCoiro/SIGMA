import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { CLAVE_SESION, cerrarSesion } from '../api/sesionApi'

/** Cierra la sesión y descarta los datos en caché del usuario, aunque el pedido falle. */
export function useCerrarSesion() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  return useMutation({
    mutationFn: cerrarSesion,
    onSettled: () => {
      navigate('/iniciar-sesion', { replace: true })
      queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== CLAVE_SESION[0] })
      queryClient.setQueryData(CLAVE_SESION, null)
    },
  })
}
