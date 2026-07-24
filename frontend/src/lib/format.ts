/** Shared formatting helpers so date/sha/number presentation never drifts between screens. */

const RELATIVE_UNITS: [limitSeconds: number, divisor: number, unit: Intl.RelativeTimeFormatUnit][] = [
  [60, 1, 'second'],
  [3600, 60, 'minute'],
  [86400, 3600, 'hour'],
  [604800, 86400, 'day'],
  [2629800, 604800, 'week'],
  [31557600, 2629800, 'month'],
]

const relativeFormatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

/** "3 hours ago" - used in queues and timelines where exact timestamps are noise. */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) {
    return '—'
  }
  const deltaSeconds = (then - Date.now()) / 1000
  const magnitude = Math.abs(deltaSeconds)

  for (const [limit, divisor, unit] of RELATIVE_UNITS) {
    if (magnitude < limit) {
      return relativeFormatter.format(Math.round(deltaSeconds / divisor), unit)
    }
  }
  return relativeFormatter.format(Math.round(deltaSeconds / 31557600), 'year')
}

/** Full timestamp for audit surfaces, where precision matters more than readability. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

export function formatDate(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString()
}

export function shortSha(sha: string): string {
  return sha.slice(0, 7)
}

/** Deep link to the exact changed line on GitHub - the developer's "what do I fix" path. */
export function githubLineUrl(
  repositoryFullName: string,
  commitSha: string,
  filePath: string,
  lineNumber: number | null,
): string {
  const base = `https://github.com/${repositoryFullName}/blob/${commitSha}/${filePath}`
  return lineNumber === null ? base : `${base}#L${lineNumber}`
}

/** Sums the values of a Partial<Record<Enum, number>> bucket map. */
export function sumBuckets(buckets: Partial<Record<string, number>> | undefined): number {
  if (!buckets) {
    return 0
  }
  return Object.values(buckets).reduce<number>((total, value) => total + (value ?? 0), 0)
}

export function percent(part: number, total: number): number {
  return total <= 0 ? 0 : Math.round((part / total) * 100)
}
