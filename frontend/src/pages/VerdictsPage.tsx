import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { verdictService } from '../services/verdictService'
import { repositoryService } from '../services/repositoryService'
import type { VerdictOutcome, VerdictSummary } from '../types/verdict'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const OUTCOME_OPTIONS: VerdictOutcome[] = ['APPROVED', 'BLOCKED']
const PAGE_SIZE = 20

// Deliberately emerald/red - the classic go/stop pairing - not the amber/orange
// severity gradient used for Policy/Security findings, and not AI Review's violet.
// The Verdict is the platform's governance decision, not a finding among many;
// it must read as visually distinct from both (Sprint 5 Milestone 3).
const OUTCOME_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  BLOCKED: 'bg-red-100 text-red-800',
}

export function VerdictsPage() {
  const [page, setPage] = useState<PageResponse<VerdictSummary> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [outcome, setOutcome] = useState<VerdictOutcome | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    verdictService
      .list({
        outcome: outcome || undefined,
        repositoryId: repositoryId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
      })
      .then(setPage)
      .finally(() => setIsLoading(false))
  }, [outcome, repositoryId, pageNumber])

  return (
    <AppLayout>
      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Verdicts</h1>
        <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-white">
          Governance Decision
        </span>
      </div>

      <div className="mb-4 flex gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={outcome}
          onChange={(event) => {
            setPageNumber(0)
            setOutcome(event.target.value as VerdictOutcome | '')
          }}
        >
          <option value="">All outcomes</option>
          {OUTCOME_OPTIONS.map((option) => (
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
                <th className="px-4 py-2">Outcome</th>
                <th className="px-4 py-2">Reasons</th>
                <th className="px-4 py-2">Decided</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {page?.content.length ? (
                page.content.map((verdict) => (
                  <tr key={verdict.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 text-slate-700">{verdict.repositoryFullName}</td>
                    <td className="px-4 py-2">
                      <Link to={`/verdicts/${verdict.id}`} className="text-slate-900 hover:underline">
                        #{verdict.pullRequestNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${OUTCOME_STYLES[verdict.outcome]}`}>
                        {verdict.outcome}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-slate-700">{verdict.reasonsTotal}</td>
                    <td className="px-4 py-2 text-slate-500">{new Date(verdict.createdAt).toLocaleString()}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                    No verdicts found.
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
