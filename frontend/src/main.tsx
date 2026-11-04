import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { LayoutAdmin } from './admin/LayoutAdmin'
import { PaginaDetalleNegocio } from './admin/PaginaDetalleNegocio'
import { PaginaNegocios } from './admin/PaginaNegocios'
import { PaginaAjustes } from './ajustes/PaginaAjustes'
import { ErrorApi } from './api/cliente'
import { PaginaCrearContrasena } from './auth/PaginaCrearContrasena'
import { PaginaCatalogo } from './catalogo/PaginaCatalogo'
import { PaginaChat } from './chat/PaginaChat'
import { PaginaProducto } from './catalogo/PaginaProducto'
import { PaginaLogin } from './auth/PaginaLogin'
import { RutaOnboarding } from './auth/RutaOnboarding'
import { RutaProtegida } from './auth/RutaProtegida'
import { reaccionarAErrorDeApi } from './auth/useSesion'
import './index.css'
import { LayoutNegocio } from './negocio/LayoutNegocio'
import { AsistenteOnboarding } from './onboarding/AsistenteOnboarding'
import { PaginaMisVideos } from './videos/PaginaMisVideos'
import { PaginaStoryboard } from './videos/PaginaStoryboard'

const clienteConsultas: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error) => reaccionarAErrorDeApi(clienteConsultas, error) }),
  mutationCache: new MutationCache({ onError: (error) => reaccionarAErrorDeApi(clienteConsultas, error) }),
  defaultOptions: {
    queries: {
      // Un 401/403 no se arregla reintentando; un fallo de red (estado 0) o un 5xx sí puede.
      retry: (intentos, error) =>
        !(error instanceof ErrorApi && error.estado >= 400 && error.estado < 500) && intentos < 2,
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
    children: [
      { path: 'bienvenida', element: <AsistenteOnboarding /> },
      {
        element: <RutaOnboarding />,
        children: [
          {
            element: <LayoutNegocio />,
            children: [
              { index: true, element: <PaginaChat /> },
              { path: 'catalogo', element: <PaginaCatalogo /> },
              { path: 'catalogo/:id', element: <PaginaProducto /> },
              { path: 'videos', element: <PaginaMisVideos /> },
              { path: 'videos/:id', element: <PaginaStoryboard /> },
              { path: 'ajustes', element: <PaginaAjustes /> },
            ],
          },
        ],
      },
    ],
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
