import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { UsuariosPage } from '../features/administracion/pages/UsuariosPage'
import { InicioSesionPage } from '../features/autenticacion/pages/InicioSesionPage'
import { EstadoPage } from '../features/estado/pages/EstadoPage'
import { InicioPage } from './InicioPage'
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
        children: [
          { index: true, element: <InicioPage /> },
          {
            element: <RutaProtegida roles={['JEFATURA']} />,
            children: [
              { path: 'usuarios', element: <UsuariosPage /> },
              { path: 'estado', element: <EstadoPage /> },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NoEncontradaPage /> },
]

export const router = createBrowserRouter(rutas)
