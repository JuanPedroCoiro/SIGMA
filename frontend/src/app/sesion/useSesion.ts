import { useContext } from 'react'
import { SesionContext } from './sesion'

export function useSesion() {
  return useContext(SesionContext)
}
