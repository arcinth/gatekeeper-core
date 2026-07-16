import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { policyFindingService } from '../services/policyFindingService'
import { repositoryService } from '../services/repositoryService'
import type { PolicyCategory, PolicyFinding, PolicySeverity } from '../types/policyFinding'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const SEVERITY_OPTIONS: PolicySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const CATEGORY_OPTIONS: PolicyCategory[] = ['MAINTAINABILITY', 'CODE_QUALITY']
const PAGE_SIZE = 20

// Same severity gradient as SecurityFindingsPage/AnalysisRunDetailPage -
// Policy and Security findings must read as the same kind of thing (a
// deterministic finding), not two different visual languages.
const SEVERITY_STYLES: Record<PolicySeverity, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'severity,desc', label: 'Severity (High to Low)' },
  { value: 'severity,asc', label: 'Severity (Low to High)' },
  { value: 'createdAt,desc', label: 'Newest First' },
  { value: 'createdAt,asc', label: 'Oldest First' },
]

export function PolicyFindingsPage() {
  const [page, setPage] = useState<PageResponse<PolicyFinding> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [severity, setSeverity] = useState<PolicySeverity | ''>('')
  const [category, setCategory] = useState<PolicyCategory | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [ruleIdInput, setRuleIdInput] = useState('')
  const [ruleId, setRuleId] = useState('')
  const [sort, setSort] = useState(SORT_OPTIONS[0].value)
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    policyFindingService
      .list({
        severity: severity || undefined,
        category: category || undefined,
        repositoryId: repositoryId || undefined,
        ruleId: ruleId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
        sort,
      })
      .then(setPage)
      .finally(() => setIsLoading(false))
  }, [severity, category, repositoryId, ruleId, sort, pageNumber])

  function applyRuleIdFilter() {
    setPageNumber(0)
    setRuleId(ruleIdInput.trim())
  }

  return (
    <AppLayout>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Policy Findings</h1>

      <div className="mb-4 flex flex-wrap gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={severity}
          onChange={(event) => {
            setPageNumber(0)
            setSeverity(event.target.value as PolicySeverity | '')
          }}
        >
          <option value="">All severities</option>
          {SEVERITY_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>

        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={category}
          onChange={(event) => {
            setPageNumber(0)
            setCategory(event.target.value as PolicyCategory | '')
          }}
        >
          <option value="">All categories</option>
          {CATEGORY_OPTIONS.map((option) => (
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

        <input
          type="text"
          placeholder="Filter by rule ID..."
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={ruleIdInput}
          onChange={(event) => setRuleIdInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              applyRuleIdFilter()
            }
          }}
          onBlur={applyRuleIdFilter}
        />

        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={sort}
          onChange={(event) => {
            setPageNumber(0)
            setSort(event.target.value)
          }}
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
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
                <th className="px-4 py-2">Severity</th>
                <th className="px-4 py-2">Rule</th>
                <th className="px-4 py-2">Repository</th>
                <th className="px-4 py-2">Pull Request</th>
                <th className="px-4 py-2">Location</th>
                <th className="px-4 py-2">Message</th>
                <th className="px-4 py-2">Found</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {page?.content.length ? (
                page.content.map((finding) => (
                  <tr key={finding.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_STYLES[finding.severity]}`}>
                        {finding.severity}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-slate-700">{finding.ruleId}</td>
                    <td className="px-4 py-2 text-slate-700">{finding.repositoryFullName}</td>
                    <td className="px-4 py-2">
                      <Link to={`/analysis-runs/${finding.analysisRunId}`} className="text-slate-900 hover:underline">
                        #{finding.pullRequestNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-2 text-slate-500">
                      {finding.filePath}:{finding.lineNumber}
                    </td>
                    <td className="px-4 py-2 text-slate-700">{finding.message}</td>
                    <td className="px-4 py-2 text-slate-500">{new Date(finding.createdAt).toLocaleString()}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={7} className="px-4 py-6 text-center text-slate-500">
                    No policy findings found.
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
