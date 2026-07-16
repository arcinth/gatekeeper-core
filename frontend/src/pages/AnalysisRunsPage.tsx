import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { analysisRunService } from '../services/analysisRunService'
import { repositoryService } from '../services/repositoryService'
import type { AnalysisRunStatus, AnalysisRunSummary } from '../types/analysisRun'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'
import type { VerdictOutcome } from '../types/verdict'

const STATUS_OPTIONS: AnalysisRunStatus[] = ['RECEIVED', 'QUEUED', 'IN_PROGRESS', 'COMPLETED', 'FAILED']
const PAGE_SIZE = 20

// Same emerald/red governance pairing as VerdictsPage - deliberately distinct
// from STATUS_STYLES below, so the verdict outcome never reads as "just
// another status" (Sprint 5 Milestone 3).
const VERDICT_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  BLOCKED: 'bg-red-100 text-red-800',
}

export function AnalysisRunsPage() {
  const [page, setPage] = useState<PageResponse<AnalysisRunSummary> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [status, setStatus] = useState<AnalysisRunStatus | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    analysisRunService
      .list({
        status: status || undefined,
        repositoryId: repositoryId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
      })
      .then(setPage)
      .finally(() => setIsLoading(false))
  }, [status, repositoryId, pageNumber])

  return (
    <AppLayout>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Analysis Runs</h1>

      <div className="mb-4 flex gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={status}
          onChange={(event) => {
            setPageNumber(0)
            setStatus(event.target.value as AnalysisRunStatus | '')
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

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Repository</th>
                <th className="px-4 py-2">Pull Request</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Verdict</th>
                <th className="px-4 py-2">Findings</th>
                <th className="px-4 py-2">Created</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {page?.content.length ? (
                page.content.map((run) => (
                  <tr key={run.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 text-slate-700">{run.repositoryFullName}</td>
                    <td className="px-4 py-2">
                      <Link to={`/analysis-runs/${run.id}`} className="text-slate-900 hover:underline">
                        #{run.pullRequestNumber} {run.pullRequestTitle}
                      </Link>
                    </td>
                    <td className="px-4 py-2">
                      <StatusBadge status={run.status} />
                    </td>
                    <td className="px-4 py-2">
                      {run.verdictOutcome ? (
                        <Link to={`/analysis-runs/${run.id}`}>
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-bold ${VERDICT_STYLES[run.verdictOutcome]}`}
                          >
                            {run.verdictOutcome}
                          </span>
                        </Link>
                      ) : (
                        <span className="text-slate-400">&mdash;</span>
                      )}
                    </td>
                    <td className="px-4 py-2 text-slate-700">{run.findingsTotal}</td>
                    <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                    No analysis runs found.
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

const STATUS_STYLES: Record<AnalysisRunStatus, string> = {
  RECEIVED: 'bg-slate-100 text-slate-700',
  QUEUED: 'bg-slate-100 text-slate-700',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}

function StatusBadge({ status }: { status: AnalysisRunStatus }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[status]}`}>{status}</span>
  )
}
