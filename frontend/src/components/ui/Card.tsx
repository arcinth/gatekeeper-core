import type { ReactNode } from 'react'

/**
 * Standardizes the `rounded-lg border bg-white p-6 shadow-sm` wrapper used
 * throughout the app (stat cards, info panels, banners). `border` accepts a
 * full border color override for the semantic banners (verdict outcome,
 * repository health, AI summary) that intentionally use a heavier 2px border
 * in their own tone rather than the default neutral one.
 */
export function Card({
  children,
  className = '',
  border = 'border border-slate-200',
  padding = 'p-6',
}: {
  children: ReactNode
  className?: string
  border?: string
  padding?: string
}) {
  return <div className={`rounded-lg ${border} bg-white ${padding} shadow-sm ${className}`}>{children}</div>
}
