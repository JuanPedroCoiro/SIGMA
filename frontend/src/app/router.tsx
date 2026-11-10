import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { InstalacionesPage } from '../features/activos/pages/InstalacionesPage'
import { CuadrillasPage } from '../features/administracion/pages/CuadrillasPage'
import { EditarCuadrillaPage } from '../features/administracion/pages/EditarCuadrillaPage'
import { EditarUsuarioPage } from '../features/administracion/pages/EditarUsuarioPage'
import { NuevaCuadrillaPage } from '../features/administracion/pages/NuevaCuadrillaPage'
import { NuevoUsuarioPage } from '../features/administracion/pages/NuevoUsuarioPage'
import { UsuarioDetallePage } from '../features/administracion/pages/UsuarioDetallePage'
import { UsuariosPage } from '../features/administracion/pages/UsuariosPage'
import { InicioSesionPage } from '../features/autenticacion/pages/InicioSesionPage'
import { EstadoPage } from '../features/estado/pages/EstadoPage'
import { OrdenDetallePage } from '../features/ot/pages/OrdenDetallePage'
import { RegistrarIncidentePage } from '../features/ot/pages/RegistrarIncidentePage'
import { InicioPage } from './InicioPage'
import { Layout } from './layout/Layout'
import { NoEncontradaPage } from './NoEncontradaPage'
import { RutaProtegida } from './rutas/RutaProtegida'
import { PendientesPage } from '../features/pendientes/pages/PendientesPage'

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
            element: <RutaProtegida roles={['JEFATURA', 'OPERADOR']} />,
            children: [
              { path: 'instalaciones', element: <InstalacionesPage /> },
              { path: 'pendientes', element: <PendientesPage /> },
              { path: 'incidentes/nuevo', element: <RegistrarIncidentePage /> },
            ],
          },
          {
            // El técnico solo ve las OTs de su cuadrilla: lo verifica el backend.
            element: <RutaProtegida roles={['JEFATURA', 'OPERADOR', 'TECNICO']} />,
            children: [{ path: 'ordenes/:id', element: <OrdenDetallePage /> }],
          },
          {
            element: <RutaProtegida roles={['JEFATURA']} />,
            children: [
              { path: 'usuarios', element: <UsuariosPage /> },
              { path: 'usuarios/nuevo', element: <NuevoUsuarioPage /> },
              { path: 'usuarios/:id', element: <UsuarioDetallePage /> },
              { path: 'usuarios/:id/editar', element: <EditarUsuarioPage /> },
              { path: 'cuadrillas', element: <CuadrillasPage /> },
              { path: 'cuadrillas/nueva', element: <NuevaCuadrillaPage /> },
              {
                path: 'cuadrillas/:id/editar',
                element: <EditarCuadrillaPage />,
              },
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
