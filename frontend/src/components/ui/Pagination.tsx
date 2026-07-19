import { Button } from './Button'

/** Consolidates the "Page X of Y" + Previous/Next footer, previously copy-pasted identically across six pages. */
export function Pagination({
  page,
  totalPages,
  first,
  last,
  onPrevious,
  onNext,
}: {
  page: number
  totalPages: number
  first: boolean
  last: boolean
  onPrevious: () => void
  onNext: () => void
}) {
  if (totalPages <= 1) {
    return null
  }
  return (
    <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm text-slate-600">
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" size="sm" disabled={first} onClick={onPrevious}>
          Previous
        </Button>
        <Button variant="secondary" size="sm" disabled={last} onClick={onNext}>
          Next
        </Button>
      </div>
    </div>
  )
}
