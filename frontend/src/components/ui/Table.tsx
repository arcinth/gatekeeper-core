import type { ReactNode } from 'react'

/**
 * Dense data table (Product Experience spec, §09): hairline borders, no zebra,
 * hover row highlight, mono uppercase headers. The footer renders outside the
 * horizontal scroll region so pagination stays put when a wide table scrolls.
 */
export function Table({ children, footer }: { children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="overflow-hidden rounded-lg border border-line bg-surface">
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">{children}</table>
      </div>
      {footer}
    </div>
  )
}

export function TableHead({ children }: { children: ReactNode }) {
  return (
    <thead className="border-b border-line bg-surface-2 text-left font-mono text-[10px] uppercase tracking-[0.09em] text-faint">
      {children}
    </thead>
  )
}

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-line-soft">{children}</tbody>
}

/** Standard header cell - keeps padding and nowrap consistent across every table. */
export function Th({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return <th className={`whitespace-nowrap px-4 py-2.5 font-semibold ${className}`}>{children}</th>
}

export function Td({ children, className = '' }: { children?: ReactNode; className?: string }) {
  return <td className={`px-4 py-3 align-middle text-muted ${className}`}>{children}</td>
}

export function Tr({
  children,
  className = '',
  onClick,
}: {
  children: ReactNode
  className?: string
  onClick?: () => void
}) {
  return (
    <tr
      onClick={onClick}
      className={`transition-colors hover:bg-surface-2 ${onClick ? 'cursor-pointer' : ''} ${className}`}
    >
      {children}
    </tr>
  )
}
