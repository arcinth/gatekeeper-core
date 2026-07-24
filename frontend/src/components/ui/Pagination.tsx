import { ChevronLeft, ChevronRight } from 'lucide-react'
import { buttonClasses } from './buttonClasses'

export function Pagination({
  page,
  totalPages,
  totalElements,
  first,
  last,
  onPrevious,
  onNext,
}: {
  page: number
  totalPages: number
  totalElements?: number
  first: boolean
  last: boolean
  onPrevious: () => void
  onNext: () => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <div className="flex items-center justify-between gap-4 border-t border-line px-4 py-2.5">
      <span className="font-mono text-[11px] text-faint">
        Page {page + 1} of {totalPages}
        {totalElements !== undefined && ` · ${totalElements} total`}
      </span>
      <div className="flex gap-2">
        <button type="button" className={buttonClasses('secondary', 'sm')} disabled={first} onClick={onPrevious}>
          <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />
          Previous
        </button>
        <button type="button" className={buttonClasses('secondary', 'sm')} disabled={last} onClick={onNext}>
          Next
          <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
