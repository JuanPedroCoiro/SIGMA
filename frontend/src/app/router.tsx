import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { EstadoPage } from '../features/estado/pages/EstadoPage'
import { InicioSesionPage } from '../features/autenticacion/pages/InicioSesionPage'
import { Layout } from './layout/Layout'
import { NoEncontradaPage } from './NoEncontradaPage'
import { RutaProtegida } from './rutas/RutaProtegida'

export const rutas: RouteObject[] = [
  { path: '/iniciar-sesion', element: <InicioSesionPage /> },
  {
    element: <RutaProtegida />,
    children: [
      {
        element: <Layout />,
        children: [{ index: true, element: <EstadoPage /> }],
      },
    ],
  },
  { path: '*', element: <NoEncontradaPage /> },
]

export const router = createBrowserRouter(rutas)
