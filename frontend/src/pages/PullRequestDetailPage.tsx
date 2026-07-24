import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { ExternalLink, History, Sparkles } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface, SectionHeading } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { Tabs, type TabItem } from '../components/ui/Tabs'
import { Label, Select, Textarea } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonDetail } from '../components/ui/Skeleton'
import { buttonClasses } from '../components/ui/buttonClasses'
import { VerdictBanner } from '../components/domain/VerdictBanner'
import { FindingCard } from '../components/domain/FindingCard'
import {
  aiReviewStatusTone,
  analysisRunStatusTone,
  humanize,
  pullRequestStatusTone,
  reviewDecisionTone,
  severityTone,
} from '../components/ui/tones'
import { formatRelativeTime, githubLineUrl, shortSha } from '../lib/format'
import { pullRequestService } from '../services/pullRequestService'
import { reportService } from '../services/reportService'
import { reviewDecisionService } from '../services/reviewDecisionService'
import type { PullRequestAnalysisRunReference, PullRequestDetail } from '../types/pullRequest'
import type { ReportDetail } from '../types/report'
import type { ReviewDecision, ReviewDecisionType } from '../types/reviewDecision'

type EvidenceTab = 'security' | 'policy' | 'ai'

/**
 * One pull request, one story (Product Experience spec, §04 and §08).
 *
 * This page absorbs what were four separate destinations - the analysis run
 * detail, the verdict detail, the AI review detail and the engineering report.
 * A user reconstructing a governance decision by hopping between those pages
 * was the single largest UX failure in the product, so the narrative order
 * here is fixed and identical for every pull request in every state:
 *
 *   1. Verdict        - what happened
 *   2. Rationale      - why it happened
 *   3. Evidence       - the proof, tabbed by engine
 *   4. Decision       - what the reviewer does about it
 *   5. History        - what happened before
 *
 * The report endpoint already aggregates policy findings, security findings,
 * AI findings, the verdict and the audit trail for a run, so the whole story
 * is two requests: the pull request, then its latest run's report.
 */
export function PullRequestDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [pullRequest, setPullRequest] = useState<PullRequestDetail | null>(null)
  const [report, setReport] = useState<ReportDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) {
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      const detail = await pullRequestService.getById(Number(id))
      setPullRequest(detail)
      const latestRun = detail.analysisRuns[0]
      setReport(latestRun ? await reportService.getByAnalysisRunId(latestRun.id) : null)
    } catch {
      setError('Failed to load this pull request.')
    } finally {
      setIsLoading(false)
    }
  }, [id])

  useEffect(() => {
    void load()
  }, [load])

  if (isLoading) {
    return (
      <AppLayout width="narrow">
        <SkeletonDetail />
      </AppLayout>
    )
  }

  if (error) {
    return (
      <AppLayout width="narrow">
        <ErrorState message={error} onRetry={() => void load()} />
      </AppLayout>
    )
  }

  if (!pullRequest) {
    return (
      <AppLayout width="narrow">
        <EmptyState title="Pull request not found" description="It may have been removed, or the link is incorrect." />
      </AppLayout>
    )
  }

  const latestRun = pullRequest.analysisRuns[0] ?? null
  const blockingReasons = report?.verdictReasons.filter((reason) => reason.blocking) ?? []

  return (
    <AppLayout
      width="narrow"
      eyebrow={pullRequest.repository.fullName}
      title={pullRequest.title}
      description={
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1 font-mono text-xs">
          <span>#{pullRequest.number}</span>
          <span aria-hidden="true">·</span>
          <span>{pullRequest.authorLogin}</span>
          <span aria-hidden="true">·</span>
          <span className="truncate">
            {pullRequest.sourceBranch} → {pullRequest.targetBranch}
          </span>
        </span>
      }
      breadcrumbs={[
        { label: 'Pull Requests', to: '/pull-requests' },
        { label: `#${pullRequest.number}` },
      ]}
      actions={
        <>
          <Chip tone={pullRequestStatusTone(pullRequest.status)}>{humanize(pullRequest.status)}</Chip>
          <a
            href={pullRequest.githubUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={buttonClasses('secondary', 'md')}
          >
            View on GitHub
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </>
      }
    >
      <div className="flex flex-col gap-5">
        {/* 1 — VERDICT: the answer, always first, always the largest thing here. */}
        <VerdictBanner
          outcome={report?.verdictOutcome ?? latestRun?.verdictOutcome ?? null}
          runStatus={latestRun?.status ?? null}
          blockingCount={blockingReasons.length}
        />

        {/* 2 — RATIONALE: why the verdict says what it says. */}
        {report && report.verdictReasons.length > 0 && (
          <Surface>
            <SectionHeading eyebrow="Rationale" title="Why this verdict" />
            <ul className="flex flex-col gap-2.5">
              {report.verdictReasons.map((reason) => (
                <li key={reason.id} className="flex items-start gap-3">
                  <Chip tone={reason.blocking ? 'block' : 'neutral'} size="sm" className="mt-0.5">
                    {reason.blocking ? 'Blocking' : 'Advisory'}
                  </Chip>
                  <div className="min-w-0">
                    <p className="text-sm text-content">{reason.message}</p>
                    <p className="mt-0.5 font-mono text-[11px] text-faint">{reason.ruleId}</p>
                  </div>
                </li>
              ))}
            </ul>
          </Surface>
        )}

        {/* 3 — EVIDENCE: the proof, with advisory AI walled off in its own lane. */}
        {report ? (
          <EvidenceSection report={report} />
        ) : (
          <Surface>
            <SectionHeading eyebrow="Evidence" title="No report published yet" />
            <p className="text-sm text-muted">
              {latestRun
                ? 'GateKeeper has not finished publishing a report for the latest commit. Evidence appears here once it completes.'
                : 'No analysis has run against this pull request, so there is no evidence to show yet.'}
            </p>
          </Surface>
        )}

        {/* 4 — DECISION: where the reviewer acts, always in the same place. */}
        <DecisionSection latestRun={latestRun} />

        {/* 5 — HISTORY: everything that happened before, collapsed by default. */}
        <HistorySection analysisRuns={pullRequest.analysisRuns} report={report} />
      </div>
    </AppLayout>
  )
}

/**
 * Evidence tabs. The blocking engine is auto-selected so a developer arriving
 * from a failed GitHub check lands directly on what stopped them, and the AI
 * tab is visually and textually marked advisory so it can never be mistaken
 * for a governance input.
 */
function EvidenceSection({ report }: { report: ReportDetail }) {
  const initialTab: EvidenceTab = report.securityFindings.length > 0 ? 'security' : report.policyFindings.length > 0 ? 'policy' : 'security'
  const [activeTab, setActiveTab] = useState<EvidenceTab>(initialTab)

  const tabs: TabItem[] = useMemo(
    () => [
      { id: 'security', label: 'Security', count: report.securityFindings.length, tone: 'block' },
      { id: 'policy', label: 'Policy', count: report.policyFindings.length, tone: 'warn' },
      {
        id: 'ai',
        label: 'AI Review',
        count: report.aiFindings.length,
        tone: 'ai',
        suffix: (
          <Chip tone="ai" size="sm">
            Advisory
          </Chip>
        ),
      },
    ],
    [report],
  )

  return (
    <Surface padding="p-0">
      <div className="px-5 pt-5">
        <SectionHeading
          eyebrow="Evidence"
          title="What GateKeeper found"
          actions={
            <Chip tone={aiReviewStatusTone(report.aiReviewStatus)}>AI {humanize(report.aiReviewStatus)}</Chip>
          }
        />
      </div>

      <Tabs items={tabs} activeId={activeTab} onChange={(id) => setActiveTab(id as EvidenceTab)} className="px-5" />

      <div className="p-5">
        {activeTab === 'security' && (
          <FindingList
            emptyTitle="No security findings"
            emptyDescription="The Security Engine found no secret exposure or insecure cryptography in this commit."
            findings={report.securityFindings.map((finding) => ({
              key: `security-${finding.id}`,
              ruleId: finding.ruleId,
              severityLabel: finding.severity,
              severityTone: severityTone(finding.severity),
              category: finding.category,
              filePath: finding.filePath,
              lineNumber: finding.lineNumber,
              message: finding.message,
              recommendation: finding.recommendation,
              sourceUrl: githubLineUrl(
                report.repository.fullName,
                report.commitSha,
                finding.filePath,
                finding.lineNumber,
              ),
              defaultExpanded: finding.severity === 'CRITICAL' || finding.severity === 'HIGH',
            }))}
          />
        )}

        {activeTab === 'policy' && (
          <FindingList
            emptyTitle="No policy findings"
            emptyDescription="This commit satisfies every enabled policy rule for your organization."
            findings={report.policyFindings.map((finding) => ({
              key: `policy-${finding.id}`,
              ruleId: finding.ruleId,
              severityLabel: finding.severity,
              severityTone: severityTone(finding.severity),
              category: finding.category,
              filePath: finding.filePath,
              lineNumber: finding.lineNumber,
              message: finding.message,
              recommendation: finding.recommendation,
              sourceUrl: githubLineUrl(
                report.repository.fullName,
                report.commitSha,
                finding.filePath,
                finding.lineNumber,
              ),
              defaultExpanded: finding.severity === 'CRITICAL',
            }))}
          />
        )}

        {activeTab === 'ai' && (
          <>
            <div className="mb-4 flex items-start gap-3 rounded-md border border-ai-line bg-ai-bg p-3">
              <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-ai" aria-hidden="true" />
              <p className="text-sm text-muted">
                AI review is <strong className="text-content">advisory only</strong>. Nothing on this tab influenced the
                governance verdict above — that comes exclusively from the deterministic Policy and Security engines.
              </p>
            </div>
            <FindingList
              emptyTitle="No AI suggestions"
              emptyDescription="The AI reviewer raised nothing for this commit."
              findings={report.aiFindings.map((finding) => ({
                key: `ai-${finding.id}`,
                ruleId: humanize(finding.type),
                severityLabel: `${finding.confidence} confidence`,
                severityTone: 'ai' as const,
                filePath: finding.filePath,
                lineNumber: finding.lineNumber,
                message: finding.message,
                recommendation: finding.recommendation,
                sourceUrl: githubLineUrl(
                  report.repository.fullName,
                  report.commitSha,
                  finding.filePath,
                  finding.lineNumber,
                ),
              }))}
            />
          </>
        )}
      </div>
    </Surface>
  )
}

function FindingList({
  findings,
  emptyTitle,
  emptyDescription,
}: {
  findings: (Omit<Parameters<typeof FindingCard>[0], 'key'> & { key: string })[]
  emptyTitle: string
  emptyDescription: string
}) {
  if (findings.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />
  }
  return (
    <div className="flex flex-col gap-2.5">
      {findings.map(({ key, ...finding }) => (
        <FindingCard key={key} {...finding} />
      ))}
    </div>
  )
}

/**
 * Reviewer actions attach to the latest analysis run - the run currently
 * relevant for review. Unchanged in behavior from the previous page: still
 * write-once (a changed mind appends a new decision), still no self-review or
 * role gating beyond what the backend itself enforces.
 */
function DecisionSection({ latestRun }: { latestRun: PullRequestAnalysisRunReference | null }) {
  const [history, setHistory] = useState<ReviewDecision[]>([])
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [decision, setDecision] = useState<ReviewDecisionType>('APPROVED')
  const [comment, setComment] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    if (!latestRun) {
      setIsLoadingHistory(false)
      return
    }
    setIsLoadingHistory(true)
    setHistoryError(null)
    reviewDecisionService
      .list(latestRun.id)
      .then(setHistory)
      .catch(() => setHistoryError('Failed to load review history.'))
      .finally(() => setIsLoadingHistory(false))
  }, [latestRun])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!latestRun) {
      return
    }
    setIsSubmitting(true)
    setSubmitError(null)
    try {
      const created = await reviewDecisionService.create(latestRun.id, {
        decision,
        comment: comment.trim() ? comment.trim() : undefined,
      })
      setHistory((current) => [created, ...current])
      setComment('')
    } catch {
      setSubmitError('Failed to record your decision. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Surface>
      <SectionHeading eyebrow="Decision" title="Record your review" />

      {!latestRun ? (
        <p className="text-sm text-muted">There is no analysis run to review yet.</p>
      ) : (
        <>
          <form onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="sm:w-40">
              <Label htmlFor="decision">Decision</Label>
              <Select
                id="decision"
                value={decision}
                onChange={(event) => setDecision(event.target.value as ReviewDecisionType)}
              >
                <option value="APPROVED">Approve</option>
                <option value="REJECTED">Reject</option>
              </Select>
            </div>
            <div className="min-w-0 flex-1">
              <Label htmlFor="comment">Comment (optional)</Label>
              <Textarea
                id="comment"
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                rows={1}
                maxLength={2000}
                placeholder="Add context for this decision…"
              />
            </div>
            <button type="submit" className={buttonClasses('primary', 'md')} disabled={isSubmitting}>
              {isSubmitting ? 'Recording…' : 'Record decision'}
            </button>
          </form>

          {submitError && <ErrorState message={submitError} className="mt-3" />}

          <div className="mt-5 border-t border-line-soft pt-4">
            {isLoadingHistory ? (
              <p className="text-sm text-muted">Loading review history…</p>
            ) : historyError ? (
              <ErrorState message={historyError} />
            ) : history.length === 0 ? (
              <p className="text-sm text-muted">No review decisions recorded yet.</p>
            ) : (
              <ul className="flex flex-col gap-3">
                {history.map((entry) => (
                  <li key={entry.id} className="flex items-start gap-3">
                    <Chip tone={reviewDecisionTone(entry.decision)} className="mt-0.5">
                      {humanize(entry.decision)}
                    </Chip>
                    <div className="min-w-0">
                      <p className="text-sm text-content">
                        {entry.reviewerName}
                        <span className="ml-2 font-mono text-[11px] text-faint">
                          {formatRelativeTime(entry.createdAt)}
                        </span>
                      </p>
                      {entry.comment && <p className="mt-0.5 text-sm text-muted">{entry.comment}</p>}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </Surface>
  )
}

/** Analysis history plus the run's own audit trail, collapsed by default. */
function HistorySection({
  analysisRuns,
  report,
}: {
  analysisRuns: PullRequestAnalysisRunReference[]
  report: ReportDetail | null
}) {
  const [isOpen, setIsOpen] = useState(false)

  return (
    <Surface>
      <button
        type="button"
        onClick={() => setIsOpen((current) => !current)}
        aria-expanded={isOpen}
        className="flex w-full items-center justify-between gap-3 text-left"
      >
        <span className="flex items-center gap-2.5">
          <History className="h-4 w-4 text-faint" aria-hidden="true" />
          <span className="text-base font-semibold tracking-tight text-content">History</span>
          <span className="tabular font-mono text-[11px] text-faint">
            {analysisRuns.length} run{analysisRuns.length === 1 ? '' : 's'}
          </span>
        </span>
        <span className="font-mono text-[11px] text-faint">{isOpen ? 'Hide' : 'Show'}</span>
      </button>

      {isOpen && (
        <div className="mt-5 flex flex-col gap-6 border-t border-line-soft pt-5">
          <div>
            <p className="mb-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Analysis runs</p>
            <ul className="flex flex-col gap-2">
              {analysisRuns.map((run) => (
                <li key={run.id} className="flex flex-wrap items-center gap-2.5">
                  <Chip tone={analysisRunStatusTone(run.status)} size="sm">
                    {humanize(run.status)}
                  </Chip>
                  <span className="font-mono text-xs text-content">{shortSha(run.commitSha)}</span>
                  <span className="font-mono text-[11px] text-faint">{humanize(run.triggerReason)}</span>
                  {run.verdictOutcome && (
                    <Chip tone={run.verdictOutcome === 'APPROVED' ? 'pass' : 'block'} size="sm">
                      {run.verdictOutcome === 'APPROVED' ? 'Cleared' : 'Blocked'}
                    </Chip>
                  )}
                  <span className="ml-auto font-mono text-[11px] text-faint">{formatRelativeTime(run.createdAt)}</span>
                </li>
              ))}
            </ul>
          </div>

          {report && report.auditTrail.length > 0 && (
            <div>
              <p className="mb-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Audit trail</p>
              <ul className="flex flex-col gap-2">
                {report.auditTrail.map((entry) => (
                  <li key={entry.id} className="flex flex-wrap items-baseline gap-2.5">
                    <span className="font-mono text-[11px] text-accent">{humanize(entry.eventType)}</span>
                    <span className="min-w-0 flex-1 text-sm text-muted">{entry.summary}</span>
                    <span className="font-mono text-[11px] text-faint">{formatRelativeTime(entry.occurredAt)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </Surface>
  )
}
