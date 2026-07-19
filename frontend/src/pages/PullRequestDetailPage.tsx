import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ExternalLink } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { buttonClasses } from '../components/ui/buttonClasses'
import { Card } from '../components/ui/Card'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import {
  ANALYSIS_RUN_STATUS_TONES,
  PULL_REQUEST_STATUS_TONES,
  REVIEW_DECISION_TONES,
  VERDICT_OUTCOME_TONES,
} from '../components/ui/badgeTones'
import { pullRequestService } from '../services/pullRequestService'
import { reviewDecisionService } from '../services/reviewDecisionService'
import type { PullRequestAnalysisRunReference, PullRequestDetail } from '../types/pullRequest'
import type { ReviewDecision, ReviewDecisionType } from '../types/reviewDecision'

export function PullRequestDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [pullRequest, setPullRequest] = useState<PullRequestDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) {
      return
    }
    setIsLoading(true)
    setError(null)
    pullRequestService
      .getById(Number(id))
      .then(setPullRequest)
      .catch(() => setError('Failed to load this pull request.'))
      .finally(() => setIsLoading(false))
  }, [id])

  if (isLoading) {
    return (
      <AppLayout>
        <LoadingSpinner />
      </AppLayout>
    )
  }

  if (error) {
    return (
      <AppLayout>
        <ErrorState message={error} />
      </AppLayout>
    )
  }

  if (!pullRequest) {
    return (
      <AppLayout>
        <p className="text-sm text-slate-500">Pull request not found.</p>
      </AppLayout>
    )
  }

  return (
    <AppLayout
      title={
        <>
          {pullRequest.repository.fullName} #{pullRequest.number}
        </>
      }
      description={pullRequest.title}
      breadcrumbs={[
        { label: 'Pull Requests', to: '/pull-requests' },
        { label: `#${pullRequest.number} ${pullRequest.title}` },
      ]}
      actions={
        <a
          href={pullRequest.githubUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={buttonClasses('secondary', 'md')}
        >
          View on GitHub <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
        </a>
      }
    >
      <Card className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <Field label="Status" value={<Badge tone={PULL_REQUEST_STATUS_TONES[pullRequest.status]}>{pullRequest.status}</Badge>} />
        <Field label="Author" value={pullRequest.authorLogin} />
        <Field label="Source branch" value={pullRequest.sourceBranch} />
        <Field label="Target branch" value={pullRequest.targetBranch} />
        <Field label="Repository owner" value={pullRequest.repository.owner} />
        <Field label="Repository name" value={pullRequest.repository.name} />
        <Field label="Created" value={new Date(pullRequest.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(pullRequest.updatedAt).toLocaleString()} />
      </Card>

      <ReviewSection latestRun={pullRequest.analysisRuns[0]} />

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Analysis History</h2>
      <AnalysisRunHistoryTable analysisRuns={pullRequest.analysisRuns} />
    </AppLayout>
  )
}

function AnalysisRunHistoryTable({ analysisRuns }: { analysisRuns: PullRequestAnalysisRunReference[] }) {
  return (
    <Table>
      <TableHead>
        <tr>
          <th className="px-4 py-2">Commit</th>
          <th className="px-4 py-2">Trigger</th>
          <th className="px-4 py-2">Status</th>
          <th className="px-4 py-2">Verdict</th>
          <th className="px-4 py-2">Created</th>
        </tr>
      </TableHead>
      <TableBody>
        {analysisRuns.length ? (
          analysisRuns.map((run) => (
            <tr key={run.id} className="hover:bg-slate-50">
              <td className="px-4 py-2">
                <Link to={`/analysis-runs/${run.id}`} className="text-slate-900 hover:underline">
                  {run.commitSha.slice(0, 7)}
                </Link>
              </td>
              <td className="px-4 py-2 text-slate-700">{run.triggerReason}</td>
              <td className="px-4 py-2">
                <Badge tone={ANALYSIS_RUN_STATUS_TONES[run.status]}>{run.status}</Badge>
              </td>
              <td className="px-4 py-2">
                {run.verdictOutcome ? (
                  <Badge tone={VERDICT_OUTCOME_TONES[run.verdictOutcome]} bold>
                    {run.verdictOutcome}
                  </Badge>
                ) : (
                  <span className="text-slate-400">&mdash;</span>
                )}
              </td>
              <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
            </tr>
          ))
        ) : (
          <EmptyTableRow colSpan={5}>No analysis runs yet for this pull request.</EmptyTableRow>
        )}
      </TableBody>
    </Table>
  )
}

/**
 * Reviewer actions attach to the PR's latest analysis run (analysisRuns[0] -
 * already newest-first from the API), the run currently relevant for review.
 * If a newer run lands mid-review, the "latest run" pointer simply moves on
 * next load - no real-time updates exist anywhere else in this app either
 * (Milestone 2 scope note). No role/self-review gating: the backend enforces
 * none, and the frontend deliberately doesn't invent one.
 */
function ReviewSection({ latestRun }: { latestRun: PullRequestAnalysisRunReference | undefined }) {
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
    <Card className="mb-6">
      <h2 className="mb-4 text-lg font-semibold text-slate-900">Review</h2>

      {!latestRun ? (
        <p className="text-sm text-slate-500">No analysis yet to review.</p>
      ) : (
        <>
          <form onSubmit={(event) => void handleSubmit(event)} className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end">
            <div>
              <label className="mb-1 block text-xs font-medium text-slate-500">Decision</label>
              <select
                value={decision}
                onChange={(event) => setDecision(event.target.value as ReviewDecisionType)}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900"
              >
                <option value="APPROVED">Approve</option>
                <option value="REJECTED">Reject</option>
              </select>
            </div>
            <div className="flex-1">
              <label className="mb-1 block text-xs font-medium text-slate-500">Comment (optional)</label>
              <textarea
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                rows={1}
                maxLength={2000}
                placeholder="Add context for this decision..."
                className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-900"
              />
            </div>
            <Button type="submit" variant="primary" disabled={isSubmitting}>
              {isSubmitting ? 'Submitting...' : 'Submit Decision'}
            </Button>
          </form>

          {submitError && <ErrorState message={submitError} />}

          {isLoadingHistory ? (
            <LoadingSpinner />
          ) : historyError ? (
            <ErrorState message={historyError} />
          ) : history.length ? (
            <ul className="divide-y divide-slate-100">
              {history.map((entry) => (
                <li key={entry.id} className="py-3 first:pt-0 last:pb-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone={REVIEW_DECISION_TONES[entry.decision]} bold>
                      {entry.decision}
                    </Badge>
                    <span className="text-sm font-medium text-slate-900">{entry.reviewerName}</span>
                    <span className="text-xs text-slate-400">{new Date(entry.createdAt).toLocaleString()}</span>
                  </div>
                  {entry.comment && <p className="mt-1 text-sm text-slate-600">{entry.comment}</p>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-slate-500">No review decisions recorded yet.</p>
          )}
        </>
      )}
    </Card>
  )
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
    </div>
  )
}
