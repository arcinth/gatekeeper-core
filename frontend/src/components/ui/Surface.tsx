import type { ReactNode } from 'react'

/**
 * The one panel primitive (replaces the old Card). Depth comes from a hairline
 * border on a layered surface rather than a drop shadow, and the radius is
 * tightened - both per the Product Experience spec's §09 move away from
 * "rounded-lg + shadow-sm on everything".
 */
export function Surface({
  children,
  className = '',
  padding = 'p-5',
  as: Tag = 'div',
}: {
  children: ReactNode
  className?: string
  padding?: string
  as?: 'div' | 'section' | 'article' | 'aside'
}) {
  return <Tag className={`rounded-lg border border-line bg-surface ${padding} ${className}`}>{children}</Tag>
}

/** Section heading used inside and above surfaces - mono eyebrow + title. */
export function SectionHeading({
  eyebrow,
  title,
  actions,
  className = '',
}: {
  eyebrow?: string
  title: ReactNode
  actions?: ReactNode
  className?: string
}) {
  return (
    <div className={`mb-3 flex flex-wrap items-end justify-between gap-3 ${className}`}>
      <div className="min-w-0">
        {eyebrow && (
          <p className="mb-1 font-mono text-[10px] uppercase tracking-[0.14em] text-accent">{eyebrow}</p>
        )}
        <h2 className="truncate text-base font-semibold tracking-tight text-content">{title}</h2>
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
