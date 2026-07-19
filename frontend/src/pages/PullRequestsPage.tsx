import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { pullRequestService } from '../services/pullRequestService'
import { repositoryService } from '../services/repositoryService'
import type { AnalysisRunStatus, PullRequestStatus } from '../types/analysisRun'
import type { PageResponse } from '../types/api'
import type { PullRequestSummary } from '../types/pullRequest'
import type { Repository } from '../types/repository'
import type { VerdictOutcome } from '../types/verdict'

const STATUS_OPTIONS: PullRequestStatus[] = ['OPEN', 'CLOSED', 'MERGED']
const PAGE_SIZE = 20

// Same badge language AnalysisRunsPage/AnalysisRunDetailPage already
// established for these same two enums - this page shows the same latest-run
// status and verdict outcome, just for a different (PR-scoped) row.
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

export function PullRequestsPage() {
  const [page, setPage] = useState<PageResponse<PullRequestSummary> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [status, setStatus] = useState<PullRequestStatus | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    setError(null)
    pullRequestService
      .list({
        status: status || undefined,
        repositoryId: repositoryId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
      })
      .then(setPage)
      .catch(() => setError('Failed to load pull requests.'))
      .finally(() => setIsLoading(false))
  }, [status, repositoryId, pageNumber])

  return (
    <AppLayout>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Pull Requests</h1>

      <div className="mb-4 flex gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={status}
          onChange={(event) => {
            setPageNumber(0)
            setStatus(event.target.value as PullRequestStatus | '')
          }}
        >
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>

        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={repositoryId}
          onChange={(event) => {
            setPageNumber(0)
            setRepositoryId(event.target.value ? Number(event.target.value) : '')
          }}
        >
          <option value="">All repositories</option>
          {repositories.map((repository) => (
            <option key={repository.id} value={repository.id}>
              {repository.fullName}
            </option>
          ))}
        </select>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Pull Request</th>
                <th className="px-4 py-2">Repository</th>
                <th className="px-4 py-2">Author</th>
                <th className="px-4 py-2">Branch</th>
                <th className="px-4 py-2">Latest Analysis</th>
                <th className="px-4 py-2">Verdict</th>
                <th className="px-4 py-2">Last Updated</th>
                <th className="px-4 py-2">GitHub</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {page?.content.length ? (
                page.content.map((pr) => (
                  <tr key={pr.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2">
                      <Link to={`/pull-requests/${pr.id}`} className="text-slate-900 hover:underline">
                        #{pr.number} {pr.title}
                      </Link>
                      <span className={`ml-2 rounded-full px-2 py-0.5 text-xs font-medium ${PR_STATUS_STYLES[pr.status]}`}>
                        {pr.status}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-slate-700">{pr.repositoryFullName}</td>
                    <td className="px-4 py-2 text-slate-700">{pr.authorLogin}</td>
                    <td className="px-4 py-2 text-slate-500">
                      {pr.sourceBranch} &rarr; {pr.targetBranch}
                    </td>
                    <td className="px-4 py-2">
                      {pr.latestAnalysisRunStatus ? (
                        <Link to={`/analysis-runs/${pr.latestAnalysisRunId}`}>
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium ${RUN_STATUS_STYLES[pr.latestAnalysisRunStatus]}`}
                          >
                            {pr.latestAnalysisRunStatus}
                          </span>
                        </Link>
                      ) : (
                        <span className="text-slate-400">&mdash;</span>
                      )}
                    </td>
                    <td className="px-4 py-2">
                      {pr.latestVerdictOutcome ? (
                        <Link to={`/analysis-runs/${pr.latestAnalysisRunId}`}>
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-bold ${VERDICT_STYLES[pr.latestVerdictOutcome]}`}
                          >
                            {pr.latestVerdictOutcome}
                          </span>
                        </Link>
                      ) : (
                        <span className="text-slate-400">&mdash;</span>
                      )}
                    </td>
                    <td className="px-4 py-2 text-slate-500">{new Date(pr.updatedAt).toLocaleString()}</td>
                    <td className="px-4 py-2">
                      <a
                        href={pr.githubUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-slate-500 hover:underline"
                      >
                        View &rarr;
                      </a>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={8} className="px-4 py-6 text-center text-slate-500">
                    No pull requests found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>

          {page && page.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm text-slate-600">
              <span>
                Page {page.page + 1} of {page.totalPages}
              </span>
              <div className="flex gap-2">
                <button
                  className="rounded-md border border-slate-300 px-3 py-1 disabled:opacity-50"
                  disabled={page.first}
                  onClick={() => setPageNumber((current) => current - 1)}
                >
                  Previous
                </button>
                <button
                  className="rounded-md border border-slate-300 px-3 py-1 disabled:opacity-50"
                  disabled={page.last}
                  onClick={() => setPageNumber((current) => current + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </AppLayout>
  )
}
