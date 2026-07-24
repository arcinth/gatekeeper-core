import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import { buttonClasses } from './buttonClasses'

/**
 * Empty and error states, together in one module because they are the same
 * shape with different intent. Per the Product Experience spec §11, an empty
 * list is never a dead end: it states the situation calmly and offers the next
 * action, and an error always explains itself and offers a retry.
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className = '',
}: {
  icon?: LucideIcon
  title: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div
      className={`flex flex-col items-center justify-center rounded-lg border border-dashed border-line bg-surface px-6 py-14 text-center ${className}`}
    >
      {Icon && <Icon className="mb-3 h-7 w-7 text-faint" aria-hidden="true" />}
      <p className="text-sm font-semibold text-content">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  )
}

export function ErrorState({
  message,
  onRetry,
  className = '',
}: {
  message: string
  onRetry?: () => void
  className?: string
}) {
  return (
    <div
      role="alert"
      className={`flex flex-wrap items-center gap-3 rounded-lg border border-block-line bg-block-bg px-4 py-3 text-sm text-content ${className}`}
    >
      <AlertTriangle className="h-4 w-4 shrink-0 text-block" aria-hidden="true" />
      <span className="min-w-0 flex-1">{message}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className={buttonClasses('secondary', 'sm')}>
          Retry
        </button>
      )}
    </div>
  )
}

/** Inline "nothing here" row for tables, matching the empty-state voice. */
export function EmptyTableRow({ colSpan, children }: { colSpan: number; children: ReactNode }) {
  return (
    <tr>
      <td colSpan={colSpan} className="px-4 py-12 text-center text-sm text-muted">
        {children}
      </td>
    </tr>
  )
}
