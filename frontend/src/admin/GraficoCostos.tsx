import { useEffect, useId, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import type { CostosDiarios } from '../api/admin'
import { formatearCosto, formatearMes } from '../componentes/formato'

/**
 * Colores de las líneas, en orden fijo: el de la marca y luego categóricos, revisados para que se distingan también
 * con daltonismo. Del séptimo negocio en adelante se juntan en "Otros", en gris.
 */
const COLORES = ['#e11d48', '#2a78d6', '#1baf7a', '#eda100', '#6250d6', '#008300']
const COLOR_OTROS = '#a8a29e'

const ALTO = 240
const MARGEN = { arriba: 12, derecha: 16, abajo: 28, izquierda: 68 }

interface Linea {
  clave: string
  nombre: string
  color: string
  serie: number[]
}

/** RF-07: costo acumulado de cada negocio día a día en el mes, en un gráfico de líneas hecho con SVG. */
export function GraficoCostos({ datos }: { datos: CostosDiarios }) {
  const idTitulo = useId()
  const idDescripcion = useId()
  const contenedor = useRef<HTMLDivElement>(null)
  const [ancho, setAncho] = useState(0)
  const [activo, setActivo] = useState<number | null>(null)

  useEffect(() => {
    const elemento = contenedor.current
    if (!elemento) return
    const observador = new ResizeObserver(([entrada]) => setAncho(entrada.contentRect.width))
    observador.observe(elemento)
    return () => observador.disconnect()
  }, [])

  const lineas = lineasDe(datos)
  const dias = datos.dias
  const ultimo = dias.length - 1
  const mes = formatearMes(datos.mes)

  const anchoPlot = Math.max(ancho - MARGEN.izquierda - MARGEN.derecha, 1)
  const altoPlot = ALTO - MARGEN.arriba - MARGEN.abajo
  const maximo = Math.max(0, ...lineas.flatMap((linea) => linea.serie))
  const pasoY = pasoRedondo(maximo > 0 ? maximo : 1, 4)
  const topeY = Math.max(pasoY, Math.ceil(maximo / pasoY - 1e-9) * pasoY)
  const decimales = decimalesDe(pasoY)
  const marcasY = Array.from({ length: Math.round(topeY / pasoY) + 1 }, (_, i) => i * pasoY)
  const marcasX = marcasDeDias(dias, anchoPlot)

  const x = (indice: number) =>
    MARGEN.izquierda + (dias.length === 1 ? anchoPlot / 2 : (indice * anchoPlot) / (dias.length - 1))
  const y = (valor: number) => MARGEN.arriba + altoPlot * (1 - valor / topeY)

  function seguirPuntero(evento: PointerEvent<SVGSVGElement>) {
    if (dias.length === 0) return
    const izquierda = evento.currentTarget.getBoundingClientRect().left
    const relativo = (evento.clientX - izquierda - MARGEN.izquierda) / anchoPlot
    setActivo(Math.min(ultimo, Math.max(0, Math.round(relativo * ultimo))))
  }

  function moverConTeclado(evento: KeyboardEvent<SVGSVGElement>) {
    if (evento.key === 'ArrowLeft' || evento.key === 'ArrowRight') {
      evento.preventDefault()
      const paso = evento.key === 'ArrowLeft' ? -1 : 1
      setActivo((actual) => Math.min(ultimo, Math.max(0, (actual ?? ultimo) + paso)))
    } else if (evento.key === 'Escape') {
      setActivo(null)
    }
  }

  const resumen =
    `Costo acumulado de IA por negocio del 1 al ${dias.length} de ${mes}. ` +
    lineas.map((linea) => `${linea.nombre}: ${formatearCosto(linea.serie[ultimo] ?? 0)}`).join('; ') +
    '.'

  return (
    <section className="rounded-xl bg-white p-4 ring-1 ring-stone-200 sm:p-5">
      <h2 id={idTitulo} className="text-base font-semibold text-stone-900">
        Costo acumulado por negocio
      </h2>
      <p className="text-sm text-stone-600">Lo que lleva cada negocio en el mes, día a día (hora de Lima).</p>

      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm">
        {lineas.map((linea) => (
          <li key={linea.clave} className="flex items-center gap-2">
            <span aria-hidden="true" className="h-0.5 w-4 rounded-full" style={{ backgroundColor: linea.color }} />
            <span className="text-stone-700">{linea.nombre}</span>
            <span className="font-semibold text-stone-900">{formatearCosto(linea.serie[ultimo] ?? 0)}</span>
          </li>
        ))}
      </ul>

      <div ref={contenedor} className="relative mt-3">
        {ancho > 0 && (
          <svg
            width={ancho}
            height={ALTO}
            role="img"
            aria-labelledby={idTitulo}
            aria-describedby={idDescripcion}
            tabIndex={0}
            className="block touch-pan-y rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-marca-600"
            onPointerMove={seguirPuntero}
            onPointerDown={seguirPuntero}
            onPointerLeave={() => setActivo(null)}
            onKeyDown={moverConTeclado}
            onFocus={() => setActivo(ultimo >= 0 ? ultimo : null)}
            onBlur={() => setActivo(null)}
          >
            <desc id={idDescripcion}>{resumen}</desc>

            {marcasY.map((valor) => (
              <g key={valor}>
                <line
                  x1={MARGEN.izquierda}
                  x2={MARGEN.izquierda + anchoPlot}
                  y1={y(valor)}
                  y2={y(valor)}
                  stroke={valor === 0 ? '#d6d3d1' : '#f5f5f4'}
                />
                <text x={MARGEN.izquierda - 8} y={y(valor)} dy="0.32em" textAnchor="end" fontSize={11} fill="#78716c">
                  {formatearEje(valor, decimales)}
                </text>
              </g>
            ))}

            {marcasX.map((indice) => (
              <text key={indice} x={x(indice)} y={ALTO - 8} textAnchor="middle" fontSize={11} fill="#78716c">
                {dias[indice]}
              </text>
            ))}

            {activo !== null && (
              <line
                x1={x(activo)}
                x2={x(activo)}
                y1={MARGEN.arriba}
                y2={MARGEN.arriba + altoPlot}
                stroke="#a8a29e"
                strokeWidth={1}
              />
            )}

            {lineas.map((linea) => (
              <path
                key={linea.clave}
                d={linea.serie.map((valor, i) => `${i === 0 ? 'M' : 'L'}${x(i)},${y(valor)}`).join(' ')}
                fill="none"
                stroke={linea.color}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            ))}

            {lineas.map((linea) => {
              const indice = activo ?? (dias.length === 1 ? 0 : null)
              if (indice === null) return null
              return (
                <circle
                  key={linea.clave}
                  cx={x(indice)}
                  cy={y(linea.serie[indice])}
                  r={4}
                  fill={linea.color}
                  stroke="#ffffff"
                  strokeWidth={2}
                />
              )
            })}
          </svg>
        )}

        {activo !== null && (
          <div
            role="status"
            className="pointer-events-none absolute top-0 z-10 min-w-44 rounded-lg bg-white px-3 py-2 text-xs shadow-lg ring-1 ring-stone-200"
            style={
              x(activo) > ancho / 2
                ? { right: ancho - x(activo) + 10 }
                : { left: x(activo) + 10 }
            }
          >
            <p className="mb-1 font-medium text-stone-500">
              Día {dias[activo]} de {mes}
            </p>
            <ul className="space-y-0.5">
              {[...lineas]
                .sort((a, b) => b.serie[activo] - a.serie[activo])
                .map((linea) => (
                  <li key={linea.clave} className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className="h-0.5 w-3 shrink-0 rounded-full"
                      style={{ backgroundColor: linea.color }}
                    />
                    <span className="font-semibold text-stone-900">{formatearCosto(linea.serie[activo])}</span>
                    <span className="truncate text-stone-600">{linea.nombre}</span>
                  </li>
                ))}
            </ul>
          </div>
        )}
      </div>
    </section>
  )
}

/** Un color por negocio; si hay más negocios que colores, los más baratos se suman en "Otros". */
function lineasDe(datos: CostosDiarios): Linea[] {
  const negocios = datos.negocios
  const conColor = negocios.length > COLORES.length ? negocios.slice(0, COLORES.length - 1) : negocios
  const lineas: Linea[] = conColor.map((negocio, i) => ({
    clave: String(negocio.negocioId),
    nombre: negocio.nombre,
    color: COLORES[i],
    serie: negocio.serie,
  }))
  const resto = negocios.slice(conColor.length)
  if (resto.length > 0) {
    lineas.push({
      clave: 'otros',
      nombre: `Otros (${resto.length} negocios)`,
      color: COLOR_OTROS,
      serie: datos.dias.map((_, i) => resto.reduce((suma, negocio) => suma + negocio.serie[i], 0)),
    })
  }
  return lineas
}

/** Paso "redondo" (1, 2, 2,5 o 5 por una potencia de 10) para unas `marcas` divisiones del eje. */
function pasoRedondo(maximo: number, marcas: number): number {
  const bruto = maximo / marcas
  const potencia = 10 ** Math.floor(Math.log10(bruto))
  const factor = [1, 2, 2.5, 5, 10].find((f) => f * potencia >= bruto - 1e-12) ?? 10
  return factor * potencia
}

/** Decimales que necesita el paso del eje para no repetir etiquetas (al menos 2, como un monto). */
function decimalesDe(paso: number): number {
  let decimales = 2
  while (decimales < 6 && Math.abs(Math.round(paso * 10 ** decimales) - paso * 10 ** decimales) > 1e-6) {
    decimales++
  }
  return decimales
}

function formatearEje(valor: number, decimales: number): string {
  return `US$ ${valor.toLocaleString('es-PE', { minimumFractionDigits: decimales, maximumFractionDigits: decimales })}`
}

/** Índices de los días que llevan etiqueta, separados lo suficiente para que no se encimen. */
function marcasDeDias(dias: number[], anchoPlot: number): number[] {
  if (dias.length <= 1) return dias.map((_, i) => i)
  const porDia = anchoPlot / (dias.length - 1)
  const paso = [1, 2, 5, 7, 10].find((p) => p * porDia >= 28) ?? 15
  const marcas = dias.map((_, i) => i).filter((i) => i % paso === 0)
  const ultimo = dias.length - 1
  if (ultimo - marcas[marcas.length - 1] >= paso / 2) marcas.push(ultimo)
  return marcas
}
