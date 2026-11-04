import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { LayoutAdmin } from './admin/LayoutAdmin'
import { PaginaDetalleNegocio } from './admin/PaginaDetalleNegocio'
import { PaginaNegocios } from './admin/PaginaNegocios'
import { ErrorApi } from './api/cliente'
import { PaginaCrearContrasena } from './auth/PaginaCrearContrasena'
import { PaginaLogin } from './auth/PaginaLogin'
import { RutaProtegida } from './auth/RutaProtegida'
import './index.css'
import { PaginaInicioNegocio } from './negocio/PaginaInicioNegocio'

const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: {
      // Un 401/403 no se arregla reintentando.
      retry: (intentos, error) => !(error instanceof ErrorApi && error.estado < 500) && intentos < 2,
      refetchOnWindowFocus: false,
    },
  },
})

const enrutador = createBrowserRouter([
  { path: '/login', element: <PaginaLogin /> },
  { path: '/crear-contrasena', element: <PaginaCrearContrasena /> },
  {
    path: '/admin',
    element: <RutaProtegida rol="ADMIN" />,
    children: [
      {
        element: <LayoutAdmin />,
        children: [
          { index: true, element: <PaginaNegocios /> },
          { path: 'negocios/:id', element: <PaginaDetalleNegocio /> },
        ],
      },
    ],
  },
  {
    path: '/',
    element: <RutaProtegida rol="NEGOCIO" />,
    children: [{ index: true, element: <PaginaInicioNegocio /> }],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={enrutador} />
    </QueryClientProvider>
  </StrictMode>,
)
