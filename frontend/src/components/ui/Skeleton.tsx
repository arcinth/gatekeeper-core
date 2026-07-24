/**
 * Content-shaped loading placeholders (Product Experience spec, §11) - a
 * skeleton row tells the user what is coming; the single centered spinner it
 * replaces only told them to wait. `.skeleton` carries the pulse animation and
 * is disabled under prefers-reduced-motion (see index.css).
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`skeleton rounded-sm ${className}`} aria-hidden="true" />
}

/** Skeleton for a list/queue of rows - Inbox, pull requests, findings. */
export function SkeletonRows({ rows = 6, className = '' }: { rows?: number; className?: string }) {
  return (
    <div className={`divide-y divide-line-soft overflow-hidden rounded-lg border border-line bg-surface ${className}`} role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, index) => (
        <div key={index} className="flex items-center gap-4 px-4 py-3.5">
          <Skeleton className="h-4 w-16 shrink-0" />
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="hidden h-3 w-24 shrink-0 sm:block" />
          <Skeleton className="hidden h-3 w-16 shrink-0 md:block" />
        </div>
      ))}
    </div>
  )
}

/** Skeleton for a grid of stat tiles - Insights, repository governance. */
export function SkeletonTiles({ count = 4, className = '' }: { count?: number; className?: string }) {
  return (
    <div className={`grid gap-3 sm:grid-cols-2 lg:grid-cols-4 ${className}`} role="status" aria-label="Loading">
      {Array.from({ length: count }).map((_, index) => (
        <div key={index} className="rounded-lg border border-line bg-surface p-4">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="mt-3 h-7 w-16" />
        </div>
      ))}
    </div>
  )
}

/** Skeleton for a detail page body - the unified pull request view. */
export function SkeletonDetail() {
  return (
    <div className="flex flex-col gap-4" role="status" aria-label="Loading">
      <div className="rounded-lg border border-line bg-surface p-6">
        <Skeleton className="h-4 w-28" />
        <Skeleton className="mt-4 h-9 w-64" />
        <Skeleton className="mt-3 h-4 w-full max-w-md" />
      </div>
      <div className="rounded-lg border border-line bg-surface p-6">
        <Skeleton className="h-4 w-32" />
        <div className="mt-4 flex flex-col gap-3">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      </div>
    </div>
  )
}
