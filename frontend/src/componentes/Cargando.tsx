export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-sm text-stone-500" role="status">
      <span className="size-5 animate-spin rounded-full border-2 border-marca-600 border-t-transparent" aria-hidden />
      {texto}
    </div>
  )
}
