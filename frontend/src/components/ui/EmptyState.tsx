import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

/** Block-level empty state (as opposed to EmptyTableRow, which is table-row-level) - e.g. "select a repository first". */
export function EmptyState({
  icon: Icon,
  title,
  description,
}: {
  icon?: LucideIcon
  title: string
  description?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      {Icon && <Icon className="mb-3 h-8 w-8 text-slate-300" aria-hidden="true" />}
      <p className="text-sm font-medium text-slate-700">{title}</p>
      {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
    </div>
  )
}
