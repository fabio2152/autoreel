export function Marca({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-semibold tracking-tight text-stone-900 ${className}`}>
      <svg viewBox="0 0 32 32" className="size-7" aria-hidden>
        <rect width="32" height="32" rx="8" fill="#be123c" />
        <rect x="11" y="6" width="10" height="20" rx="2.5" fill="none" stroke="#fff" strokeWidth="2" />
        <path d="M14.5 12.5v7l5-3.5z" fill="#fff" />
      </svg>
      AutoReel
    </span>
  )
}
