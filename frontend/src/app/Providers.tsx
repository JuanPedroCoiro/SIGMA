import { useState, type ReactNode } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import { crearQueryClient } from './queryClient'
import { SesionProvider } from './sesion/SesionProvider'

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(crearQueryClient)

  return (
    <QueryClientProvider client={queryClient}>
      <SesionProvider>{children}</SesionProvider>
    </QueryClientProvider>
  )
}
