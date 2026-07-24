import { Link } from 'react-router-dom'
import { GitPullRequest } from 'lucide-react'
import { Chip } from '../ui/Chip'
import { analysisRunStatusTone, humanize, verdictOutcomeTone } from '../ui/tones'
import { formatRelativeTime } from '../../lib/format'
import type { PullRequestSummary } from '../../types/pullRequest'

/**
 * The Inbox / pull request list atom (Product Experience spec, §10). Verdict
 * first, then identity, then the one reason it needs attention. Blocked rows
 * carry visible weight; cleared rows recede - so the eye is pulled to red,
 * then amber, then rests.
 */
export function PullRequestRow({ pullRequest }: { pullRequest: PullRequestSummary }) {
  const isBlocked = pullRequest.latestVerdictOutcome === 'BLOCKED'

  return (
    <Link
      to={`/pull-requests/${pullRequest.id}`}
      className={`flex items-center gap-4 px-4 py-3.5 transition-colors hover:bg-surface-2 ${
        isBlocked ? 'bg-block-bg/40' : ''
      }`}
    >
      <span className="w-24 shrink-0">{renderStatus(pullRequest)}</span>

      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <GitPullRequest className="h-3.5 w-3.5 shrink-0 text-faint" aria-hidden="true" />
          <span className="truncate text-sm font-medium text-content">{pullRequest.title}</span>
        </span>
        <span className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 font-mono text-[11px] text-faint">
          <span className="truncate">{pullRequest.repositoryFullName}</span>
          <span aria-hidden="true">·</span>
          <span>#{pullRequest.number}</span>
          <span aria-hidden="true">·</span>
          <span className="truncate">{pullRequest.authorLogin}</span>
        </span>
      </span>

      <span className="hidden shrink-0 font-mono text-[11px] text-faint sm:block">
        {formatRelativeTime(pullRequest.updatedAt)}
      </span>
    </Link>
  )
}

/**
 * Verdict wins when there is one; otherwise the run status explains why there
 * isn't (still running, failed, or never analyzed).
 */
function renderStatus(pullRequest: PullRequestSummary) {
  if (pullRequest.latestVerdictOutcome) {
    return (
      <Chip tone={verdictOutcomeTone(pullRequest.latestVerdictOutcome)}>
        {pullRequest.latestVerdictOutcome === 'BLOCKED' ? 'Blocked' : 'Cleared'}
      </Chip>
    )
  }
  if (pullRequest.latestAnalysisRunStatus) {
    return (
      <Chip tone={analysisRunStatusTone(pullRequest.latestAnalysisRunStatus)}>
        {humanize(pullRequest.latestAnalysisRunStatus)}
      </Chip>
    )
  }
  return <Chip tone="neutral">No run</Chip>
}
