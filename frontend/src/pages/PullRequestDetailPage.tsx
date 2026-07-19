import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { pullRequestService } from '../services/pullRequestService'
import type { AnalysisRunStatus, PullRequestStatus } from '../types/analysisRun'
import type { PullRequestAnalysisRunReference, PullRequestDetail } from '../types/pullRequest'
import type { VerdictOutcome } from '../types/verdict'

// Same badge language as PullRequestsPage/AnalysisRunsPage/AnalysisRunDetailPage.
const RUN_STATUS_STYLES: Record<AnalysisRunStatus, string> = {
  RECEIVED: 'bg-slate-100 text-slate-700',
  QUEUED: 'bg-slate-100 text-slate-700',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}

const VERDICT_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  BLOCKED: 'bg-red-100 text-red-800',
}

const PR_STATUS_STYLES: Record<PullRequestStatus, string> = {
  OPEN: 'bg-emerald-100 text-emerald-800',
  CLOSED: 'bg-slate-100 text-slate-700',
  MERGED: 'bg-violet-100 text-violet-700',
}

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
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</div>
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
    <AppLayout>
      <Link to="/pull-requests" className="mb-4 inline-block text-sm text-slate-500 hover:underline">
        &larr; Back to Pull Requests
      </Link>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-semibold text-slate-900">
          {pullRequest.repository.fullName} #{pullRequest.number}
        </h1>
        <a
          href={pullRequest.githubUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          View on GitHub &rarr;
        </a>
      </div>
      <p className="mb-4 text-lg text-slate-800">{pullRequest.title}</p>

      <div className="mb-6 grid grid-cols-2 gap-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm md:grid-cols-4">
        <Field label="Status" value={<StatusBadge status={pullRequest.status} />} />
        <Field label="Author" value={pullRequest.authorLogin} />
        <Field label="Source branch" value={pullRequest.sourceBranch} />
        <Field label="Target branch" value={pullRequest.targetBranch} />
        <Field label="Repository owner" value={pullRequest.repository.owner} />
        <Field label="Repository name" value={pullRequest.repository.name} />
        <Field label="Created" value={new Date(pullRequest.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(pullRequest.updatedAt).toLocaleString()} />
      </div>

      {/*
        Review section reserved now so the page layout stays stable once
        Milestone 2 adds real reviewer actions here - placeholder only, no
        approve/reject/override capability yet.
      */}
      <div className="mb-6 rounded-lg border-2 border-dashed border-slate-300 bg-slate-50 p-6">
        <h2 className="mb-1 text-lg font-semibold text-slate-900">Review</h2>
        <p className="text-sm text-slate-500">Reviewer actions are not available yet - coming in a future update.</p>
      </div>

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Analysis History</h2>
      <AnalysisRunHistoryTable analysisRuns={pullRequest.analysisRuns} />
    </AppLayout>
  )
}

function AnalysisRunHistoryTable({ analysisRuns }: { analysisRuns: PullRequestAnalysisRunReference[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-2">Commit</th>
            <th className="px-4 py-2">Trigger</th>
            <th className="px-4 py-2">Status</th>
            <th className="px-4 py-2">Verdict</th>
            <th className="px-4 py-2">Created</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
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
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${RUN_STATUS_STYLES[run.status]}`}>
                    {run.status}
                  </span>
                </td>
                <td className="px-4 py-2">
                  {run.verdictOutcome ? (
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-bold ${VERDICT_STYLES[run.verdictOutcome]}`}
                    >
                      {run.verdictOutcome}
                    </span>
                  ) : (
                    <span className="text-slate-400">&mdash;</span>
                  )}
                </td>
                <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                No analysis runs yet for this pull request.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function StatusBadge({ status }: { status: PullRequestStatus }) {
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${PR_STATUS_STYLES[status]}`}>{status}</span>
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
    </div>
  )
}
