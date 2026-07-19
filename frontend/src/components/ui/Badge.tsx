import type { ReactNode } from 'react'

/**
 * Purely structural (shape/padding/weight) - every page still supplies its
 * own semantic color className from badgeTones.ts, so the deliberate color
 * distinctions already established across the app (severity gradient, AI's
 * violet, verdict's emerald/red) are preserved exactly. This just makes every
 * badge in the app the same size and shape.
 */
export function Badge({
  tone,
  bold = false,
  uppercase = false,
  className = '',
  children,
}: {
  tone: string
  bold?: boolean
  uppercase?: boolean
  className?: string
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs ${
        bold ? 'font-bold' : 'font-medium'
      } ${uppercase ? 'uppercase tracking-wide' : ''} ${tone} ${className}`}
    >
      {children}
    </span>
  )
}
