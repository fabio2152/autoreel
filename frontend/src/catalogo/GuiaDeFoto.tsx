/** Ilustración de ejemplo (sección 7.2): la foto se pide con una imagen de referencia. */
function Ejemplo() {
  return (
    <svg viewBox="0 0 90 160" className="h-24 w-auto shrink-0 rounded-lg bg-amber-50 ring-1 ring-amber-200" aria-hidden>
      <g>
        <rect x="18" y="78" width="54" height="36" rx="4" fill="#fbcfe8" />
        <rect x="18" y="74" width="54" height="10" rx="5" fill="#fff7ed" />
        <circle cx="30" cy="72" r="5" fill="#e11d48" />
        <circle cx="45" cy="70" r="5" fill="#e11d48" />
        <circle cx="60" cy="72" r="5" fill="#e11d48" />
        <rect x="12" y="114" width="66" height="4" rx="2" fill="#d6d3d1" />
      </g>
    </svg>
  )
}

export function GuiaDeFoto() {
  return (
    <div className="flex gap-3">
      <Ejemplo />
      <div>
        <p className="font-medium text-stone-800">
          La que mejor muestra tu producto: completo, bien iluminado y con lo que lo hace especial a la vista.
        </p>
        <ul className="mt-1 space-y-0.5">
          <li>☀️ Con luz natural</li>
          <li>🚫 Sin personas ni manos</li>
          <li>📱 Con el celular en vertical</li>
        </ul>
      </div>
    </div>
  )
}
