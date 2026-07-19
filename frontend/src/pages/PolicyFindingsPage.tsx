import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { SEVERITY_TONES } from '../components/ui/badgeTones'
import { policyFindingService } from '../services/policyFindingService'
import { repositoryService } from '../services/repositoryService'
import type { PolicyCategory, PolicyFinding, PolicySeverity } from '../types/policyFinding'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const SEVERITY_OPTIONS: PolicySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const CATEGORY_OPTIONS: PolicyCategory[] = ['MAINTAINABILITY', 'CODE_QUALITY']
const PAGE_SIZE = 20

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
    <AppLayout title="Policy Findings">
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
        <Table
          footer={
            page && (
              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                first={page.first}
                last={page.last}
                onPrevious={() => setPageNumber((current) => current - 1)}
                onNext={() => setPageNumber((current) => current + 1)}
              />
            )
          }
        >
          <TableHead>
            <tr>
              <th className="px-4 py-2">Severity</th>
              <th className="px-4 py-2">Rule</th>
              <th className="px-4 py-2">Repository</th>
              <th className="px-4 py-2">Pull Request</th>
              <th className="px-4 py-2">Location</th>
              <th className="px-4 py-2">Message</th>
              <th className="px-4 py-2">Found</th>
            </tr>
          </TableHead>
          <TableBody>
            {page?.content.length ? (
              page.content.map((finding) => (
                <tr key={finding.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2">
                    <Badge tone={SEVERITY_TONES[finding.severity]}>{finding.severity}</Badge>
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
              <EmptyTableRow colSpan={7}>No policy findings found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
