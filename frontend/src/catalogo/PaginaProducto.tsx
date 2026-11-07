import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  actualizarProducto,
  archivarProducto,
  CLAVE_PRODUCTOS,
  crearProducto,
  fotoDe,
  NOMBRE_FOTO,
  obtenerProducto,
  reactivarProducto,
  TIPOS_FOTO,
  type Producto,
  type TipoFoto,
} from '../api/catalogo'
import { CLAVE_CONFIGURACION, obtenerConfiguracion } from '../api/negocio'
import { Aviso } from '../componentes/Aviso'
import { Boton } from '../componentes/Boton'
import { Campo } from '../componentes/Campo'
import { Cargando } from '../componentes/Cargando'
import { SubidorDeImagen } from '../componentes/SubidorDeImagen'
import { GuiaDeFoto } from './GuiaDeFoto'

const MAX_NOMBRE = 50
const MAX_DESCRIPCION = 200

/** Crear (/catalogo/nuevo) o editar (/catalogo/:id) un producto. */
export function PaginaProducto() {
  const { id } = useParams()
  const productoId = id && id !== 'nuevo' ? Number(id) : null
  const producto = useQuery({
    queryKey: [...CLAVE_PRODUCTOS, 'detalle', productoId],
    queryFn: () => obtenerProducto(productoId!),
    enabled: productoId !== null,
  })
  const configuracion = useQuery({ queryKey: CLAVE_CONFIGURACION, queryFn: obtenerConfiguracion })

  if (configuracion.isPending || (productoId !== null && producto.isPending)) return <Cargando />
  const error = configuracion.error ?? producto.error
  if (error || !configuracion.data) return <Aviso tipo="error">{error?.message ?? 'No pudimos cargar el catálogo.'}</Aviso>
  return (
    <FormularioProducto
      key={productoId ?? 'nuevo'}
      producto={producto.data ?? null}
    />
  )
}

function FormularioProducto({ producto }: { producto: Producto | null }) {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const [nombre, setNombre] = useState(producto?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(producto?.descripcion ?? '')
  // Las temporadas no se piden en el MVP (2026-11-06): se conservan las que ya tenía el producto.
  const elegidas = producto?.temporadas ?? []
  const [fotos, setFotos] = useState<Partial<Record<TipoFoto, string>>>(() =>
    Object.fromEntries(TIPOS_FOTO.flatMap((tipo) => {
      const foto = producto && fotoDe(producto, tipo)
      return foto ? [[tipo, foto.clave]] : []
    })),
  )
  const [subiendo, setSubiendo] = useState(0)

  const alTerminar = () => {
    clienteConsultas.invalidateQueries({ queryKey: CLAVE_PRODUCTOS })
    navegar('/catalogo')
  }

  const guardar = useMutation({
    mutationFn: () => {
      const solicitud = { nombre, descripcion, temporadas: elegidas, fotos }
      return producto ? actualizarProducto(producto.id, solicitud) : crearProducto(solicitud)
    },
    onSuccess: alTerminar,
  })
  const archivar = useMutation({
    mutationFn: () =>
      producto!.estado === 'ACTIVO' ? archivarProducto(producto!.id) : reactivarProducto(producto!.id),
    onSuccess: alTerminar,
  })

  const enviar = (evento: FormEvent) => {
    evento.preventDefault()
    guardar.mutate()
  }

  const fotosFaltantes = TIPOS_FOTO.filter((tipo) => !fotos[tipo]).length

  return (
    <form onSubmit={enviar} className="mx-auto max-w-xl space-y-6 pb-24">
      <div>
        <Link to="/catalogo" className="text-sm font-medium text-marca-700 hover:underline">
          ← Catálogo
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">{producto ? 'Editar producto' : 'Nuevo producto'}</h1>
        {producto?.estado === 'ARCHIVADO' && (
          <p className="mt-1 text-sm text-stone-600">Archivado: no aparece al crear videos.</p>
        )}
      </div>

      <Campo
        etiqueta="Nombre"
        required
        maxLength={MAX_NOMBRE}
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
        placeholder="Torta Tres Leches de Fresa"
        ayuda={<span className="float-right tabular-nums">{nombre.length}/{MAX_NOMBRE}</span>}
      />

      <div>
        <label htmlFor="descripcion" className="mb-1 block text-sm font-medium text-stone-700">
          Descripción
        </label>
        <textarea
          id="descripcion"
          required
          rows={3}
          maxLength={MAX_DESCRIPCION}
          value={descripcion}
          onChange={(e) => setDescripcion(e.target.value)}
          placeholder="Sabor, relleno, ingredientes destacados…"
          className="block w-full rounded-lg border-0 bg-white px-3 py-2 text-base ring-1 ring-inset ring-stone-300 focus:ring-2 focus:ring-marca-600 sm:text-sm"
        />
        <p className="mt-1 text-xs text-stone-500">
          Es lo único que usaremos para hablar de tu producto: no inventamos sabores ni ingredientes.
          <span className="float-right tabular-nums">
            {descripcion.length}/{MAX_DESCRIPCION}
          </span>
        </p>
      </div>

      <section className="space-y-5">
        <div>
          <h2 className="font-semibold">Fotos</h2>
          <p className="text-sm text-stone-600">
            Las tres son necesarias para usar el producto en un video.
            {fotosFaltantes > 0 && ` Te ${fotosFaltantes === 1 ? 'falta 1' : `faltan ${fotosFaltantes}`}.`}
          </p>
        </div>
        {TIPOS_FOTO.map((tipo) => (
          <SubidorDeImagen
            key={tipo}
            proposito="FOTO_PRODUCTO"
            etiqueta={NOMBRE_FOTO[tipo]}
            actual={
              producto && fotos[tipo] === fotoDe(producto, tipo)?.clave ? fotoDe(producto, tipo)?.url : null
            }
            deshabilitado={guardar.isPending}
            guia={<GuiaDeFoto tipo={tipo} />}
            onSubiendo={(activo) => setSubiendo((n) => n + (activo ? 1 : -1))}
            onSubida={(imagen) => setFotos((actual) => ({ ...actual, [tipo]: imagen.clave }))}
            onQuitar={() =>
              setFotos((actual) => {
                const resto = { ...actual }
                delete resto[tipo]
                return resto
              })
            }
          />
        ))}
      </section>

      <div className="fixed inset-x-0 bottom-16 border-t border-stone-200 bg-white/95 px-4 py-3 backdrop-blur md:bottom-0">
        <div className="mx-auto flex max-w-xl flex-col gap-2">
          {(guardar.error || archivar.error) && (
            <Aviso tipo="error">{guardar.error?.message ?? archivar.error?.message}</Aviso>
          )}
          <div className="flex gap-3">
            {producto && (
              <Boton type="button" variante="secundario" onClick={() => archivar.mutate()} cargando={archivar.isPending}>
                {producto.estado === 'ACTIVO' ? 'Archivar' : 'Reactivar'}
              </Boton>
            )}
            <Boton type="submit" className="flex-1" cargando={guardar.isPending} disabled={subiendo > 0}>
              {subiendo > 0 ? 'Subiendo fotos…' : 'Guardar'}
            </Boton>
          </div>
        </div>
      </div>
    </form>
  )
}
