import type { TipoFoto } from '../api/catalogo'

const DESCRIPCION: Record<TipoFoto, string> = {
  ENTERO: 'El producto completo, de frente.',
  DETALLE: 'Muy cerca: la crema, el glaseado o la fruta.',
  PORCION: 'Una porción ya cortada, mostrando el relleno.',
}

/** Ilustraciones de ejemplo (sección 7.2): cada foto se pide con una imagen de referencia. */
function Ejemplo({ tipo }: { tipo: TipoFoto }) {
  return (
    <svg viewBox="0 0 90 160" className="h-24 w-auto shrink-0 rounded-lg bg-amber-50 ring-1 ring-amber-200" aria-hidden>
      {tipo === 'ENTERO' && (
        <g>
          <rect x="18" y="78" width="54" height="36" rx="4" fill="#fbcfe8" />
          <rect x="18" y="74" width="54" height="10" rx="5" fill="#fff7ed" />
          <circle cx="30" cy="72" r="5" fill="#e11d48" />
          <circle cx="45" cy="70" r="5" fill="#e11d48" />
          <circle cx="60" cy="72" r="5" fill="#e11d48" />
          <rect x="12" y="114" width="66" height="4" rx="2" fill="#d6d3d1" />
        </g>
      )}
      {tipo === 'DETALLE' && (
        <g>
          <rect x="0" y="70" width="90" height="90" fill="#fff7ed" />
          <circle cx="30" cy="62" r="20" fill="#e11d48" />
          <circle cx="66" cy="70" r="16" fill="#be123c" />
          <ellipse cx="24" cy="54" rx="5" ry="3" fill="#fecdd3" />
          <path d="M0 96 Q22 84 45 96 T90 96 V160 H0Z" fill="#fde68a" />
        </g>
      )}
      {tipo === 'PORCION' && (
        <g>
          <path d="M20 112 L70 112 L70 80 Z" fill="#fbcfe8" />
          <path d="M20 112 L70 80" stroke="#fff7ed" strokeWidth="6" />
          <rect x="30" y="98" width="40" height="4" fill="#e11d48" transform="rotate(-32 50 100)" />
          <rect x="12" y="114" width="66" height="4" rx="2" fill="#d6d3d1" />
        </g>
      )}
    </svg>
  )
}

export function GuiaDeFoto({ tipo }: { tipo: TipoFoto }) {
  return (
    <div className="flex gap-3">
      <Ejemplo tipo={tipo} />
      <div>
        <p className="font-medium text-stone-800">{DESCRIPCION[tipo]}</p>
        <ul className="mt-1 space-y-0.5">
          <li>☀️ Con luz natural</li>
          <li>🚫 Sin personas ni manos</li>
          <li>📱 Con el celular en vertical</li>
        </ul>
      </div>
    </div>
  )
}
