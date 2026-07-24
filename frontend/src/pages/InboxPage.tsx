import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckCircle2, ShieldAlert } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface, SectionHeading } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { PullRequestRow } from '../components/domain/PullRequestRow'
import { severityTone } from '../components/ui/tones'
import { formatRelativeTime } from '../lib/format'
import { useAuth } from '../hooks/useAuth'
import { pullRequestService } from '../services/pullRequestService'
import { securityFindingService } from '../services/securityFindingService'
import type { PullRequestSummary } from '../types/pullRequest'
import type { SecurityFinding } from '../types/securityFinding'

/**
 * The daily home (Product Experience spec, §06 Surface A).
 *
 * Replaces the old stat-wall dashboard as the landing screen. It answers
 * "what needs me right now" with work, not counts: a hero line in plain
 * language, a queue sorted by urgency, and a narrow rail of critical alerts.
 * Metrics moved to Insights, where trends actually belong.
 */
export function InboxPage() {
  const { user } = useAuth()
  const [pullRequests, setPullRequests] = useState<PullRequestSummary[]>([])
  const [criticalFindings, setCriticalFindings] = useState<SecurityFinding[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [openPullRequests, critical] = await Promise.all([
        pullRequestService.list({ status: 'OPEN', size: 50 }),
        securityFindingService.list({ severity: 'CRITICAL', size: 5, sort: 'createdAt,desc' }),
      ])
      setPullRequests(openPullRequests.content)
      setCriticalFindings(critical.content)
    } catch {
      setError('Failed to load your inbox.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const queue = [...pullRequests].sort(urgencyComparator)
  const blockedCount = queue.filter((pr) => pr.latestVerdictOutcome === 'BLOCKED').length
  const awaitingCount = queue.filter((pr) => pr.latestVerdictOutcome === 'APPROVED').length
  const runningCount = queue.filter(
    (pr) => pr.latestAnalysisRunStatus === 'IN_PROGRESS' || pr.latestAnalysisRunStatus === 'QUEUED',
  ).length

  const firstName = user?.fullName.trim().split(/\s+/)[0] ?? 'there'

  return (
    <AppLayout
      eyebrow="Inbox"
      title={`Good to see you, ${firstName}`}
      description={
        isLoading ? 'Checking what needs your attention…' : headline(blockedCount, awaitingCount, runningCount)
      }
    >
      {error && <ErrorState message={error} onRetry={() => void load()} className="mb-5" />}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section aria-label="Needs attention">
          <SectionHeading
            eyebrow="Queue"
            title="Needs your attention"
            actions={
              <Link to="/pull-requests" className="font-mono text-[11px] text-muted transition-colors hover:text-content">
                View all →
              </Link>
            }
          />

          {isLoading ? (
            <SkeletonRows rows={6} />
          ) : queue.length === 0 ? (
            <EmptyState
              icon={CheckCircle2}
              title="You're all clear"
              description="No open pull requests are waiting on GateKeeper right now."
            />
          ) : (
            <div className="divide-y divide-line-soft overflow-hidden rounded-lg border border-line bg-surface">
              {queue.map((pullRequest) => (
                <PullRequestRow key={pullRequest.id} pullRequest={pullRequest} />
              ))}
            </div>
          )}
        </section>

        <aside aria-label="Critical alerts">
          <SectionHeading eyebrow="Alerts" title="Critical findings" />
          {isLoading ? (
            <SkeletonRows rows={3} />
          ) : criticalFindings.length === 0 ? (
            <Surface>
              <p className="text-sm text-muted">
                No critical security findings are open. This is the state you want to be in.
              </p>
            </Surface>
          ) : (
            <div className="flex flex-col gap-2.5">
              {criticalFindings.map((finding) => (
                <Link
                  key={finding.id}
                  to="/security"
                  className="block rounded-lg border border-block-line bg-block-bg p-3.5 transition-colors hover:border-block"
                >
                  <div className="flex items-center gap-2">
                    <ShieldAlert className="h-3.5 w-3.5 shrink-0 text-block" aria-hidden="true" />
                    <Chip tone={severityTone(finding.severity)} size="sm">
                      {finding.severity}
                    </Chip>
                  </div>
                  <p className="mt-2 line-clamp-2 text-sm text-content">{finding.message}</p>
                  <p className="mt-1.5 truncate font-mono text-[11px] text-faint">
                    {finding.repositoryFullName} · {formatRelativeTime(finding.createdAt)}
                  </p>
                </Link>
              ))}
            </div>
          )}
        </aside>
      </div>
    </AppLayout>
  )
}

/** Plain-language status, written like a person rather than a gauge. */
function headline(blocked: number, cleared: number, running: number): string {
  if (blocked === 0 && running === 0 && cleared === 0) {
    return 'Nothing is waiting on you. No open pull requests need a decision right now.'
  }
  const parts: string[] = []
  if (blocked > 0) {
    parts.push(`${blocked} merge${blocked === 1 ? ' is' : 's are'} blocked`)
  }
  if (cleared > 0) {
    parts.push(`${cleared} ${cleared === 1 ? 'is' : 'are'} cleared to merge`)
  }
  if (running > 0) {
    parts.push(`${running} ${running === 1 ? 'is' : 'are'} still being analyzed`)
  }
  return `${parts.join(', ')}.`
}

/** Blocked first, then still-running, then everything else - most urgent to least. */
function urgencyComparator(a: PullRequestSummary, b: PullRequestSummary): number {
  const rank = (pr: PullRequestSummary): number => {
    if (pr.latestVerdictOutcome === 'BLOCKED') return 0
    if (pr.latestAnalysisRunStatus === 'FAILED') return 1
    if (pr.latestAnalysisRunStatus === 'IN_PROGRESS' || pr.latestAnalysisRunStatus === 'QUEUED') return 2
    if (!pr.latestAnalysisRunId) return 3
    return 4
  }
  const delta = rank(a) - rank(b)
  return delta !== 0 ? delta : new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
}
