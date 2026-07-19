import type { ReactNode } from 'react'

export type TableTone = 'neutral' | 'ai'

const CONTAINER_TONE: Record<TableTone, string> = {
  neutral: 'border-slate-200',
  ai: 'border-violet-200',
}

const HEAD_TONE: Record<TableTone, string> = {
  neutral: 'bg-slate-50 text-slate-500',
  ai: 'bg-violet-50 text-violet-700',
}

/**
 * Standardizes the `rounded-lg border ... shadow-sm` table card used
 * throughout the app. `footer` (typically <Pagination>) renders outside the
 * inner `overflow-x-auto` scroll region, so it stays put and visible even
 * when a wide table scrolls horizontally - previously the pagination controls
 * scrolled away together with the table content.
 */
export function Table({
  children,
  tone = 'neutral',
  footer,
}: {
  children: ReactNode
  tone?: TableTone
  footer?: ReactNode
}) {
  return (
    <div className={`rounded-lg border ${CONTAINER_TONE[tone]} bg-white shadow-sm`}>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-sm">{children}</table>
      </div>
      {footer}
    </div>
  )
}

export function TableHead({ children, tone = 'neutral' }: { children: ReactNode; tone?: TableTone }) {
  return <thead className={`text-left text-xs font-medium uppercase tracking-wide ${HEAD_TONE[tone]}`}>{children}</thead>
}

export function TableBody({ children }: { children: ReactNode }) {
  return <tbody className="divide-y divide-slate-100">{children}</tbody>
}

/** The repeated "No X found" full-width table row, standardized to one place. */
export function EmptyTableRow({ colSpan, children }: { colSpan: number; children: ReactNode }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-4 py-10 text-center text-sm text-slate-500">
        {children}
      </td>
    </tr>
  )
}
