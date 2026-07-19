import type { ReactNode } from 'react'

/**
 * Standardizes the title/eyebrow-badge/actions row every page previously
 * built by hand (with small drifts - some had an <h1>, some wrapped it with a
 * badge, some added a button row). Cross-page navigation buttons that used to
 * live here (e.g. DashboardPage's "View Pull Requests"/"View Repositories"
 * row) are gone - that's what the persistent sidebar is for now; `actions` is
 * reserved for actions specific to the current page (e.g. "View on GitHub").
 */
export function PageHeader({
  title,
  eyebrow,
  description,
  actions,
}: {
  title: ReactNode
  eyebrow?: ReactNode
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="truncate text-xl font-semibold text-slate-900">{title}</h1>
          {eyebrow}
        </div>
        {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
