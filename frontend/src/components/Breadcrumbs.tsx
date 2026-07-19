import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

export interface BreadcrumbItem {
  label: string
  to?: string
}

/**
 * Each page passes its own trail explicitly via AppLayout's `breadcrumbs`
 * prop, rather than this component deriving labels from the URL - a detail
 * page's crumb needs the actual PR title / commit / etc., which no amount of
 * path-parsing could reconstruct.
 */
export function Breadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  if (items.length < 2) {
    return null
  }
  return (
    <nav aria-label="Breadcrumb" className="mb-3 flex items-center text-sm text-slate-500">
      {items.map((item, index) => {
        const isLast = index === items.length - 1
        return (
          <span key={`${item.label}-${index}`} className="flex items-center">
            {index > 0 && <ChevronRight className="mx-1.5 h-3.5 w-3.5 shrink-0 text-slate-300" aria-hidden="true" />}
            {item.to && !isLast ? (
              <Link to={item.to} className="truncate hover:text-slate-700 hover:underline">
                {item.label}
              </Link>
            ) : (
              <span className={`truncate ${isLast ? 'font-medium text-slate-700' : ''}`}>{item.label}</span>
            )}
          </span>
        )
      })}
    </nav>
  )
}
