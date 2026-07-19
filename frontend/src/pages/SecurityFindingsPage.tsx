import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { SEVERITY_TONES } from '../components/ui/badgeTones'
import { securityFindingService } from '../services/securityFindingService'
import { repositoryService } from '../services/repositoryService'
import type { SecurityCategory, SecurityFinding, SecuritySeverity } from '../types/securityFinding'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const SEVERITY_OPTIONS: SecuritySeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const CATEGORY_OPTIONS: SecurityCategory[] = ['SECRETS_EXPOSURE', 'INSECURE_CRYPTOGRAPHY']
const PAGE_SIZE = 20

export function SecurityFindingsPage() {
  const [page, setPage] = useState<PageResponse<SecurityFinding> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [severity, setSeverity] = useState<SecuritySeverity | ''>('')
  const [category, setCategory] = useState<SecurityCategory | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    securityFindingService
      .list({
        severity: severity || undefined,
        category: category || undefined,
        repositoryId: repositoryId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
        sort: 'severity,desc',
      })
      .then(setPage)
      .finally(() => setIsLoading(false))
  }, [severity, category, repositoryId, pageNumber])

  return (
    <AppLayout title="Security Findings">
      <div className="mb-4 flex flex-wrap gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={severity}
          onChange={(event) => {
            setPageNumber(0)
            setSeverity(event.target.value as SecuritySeverity | '')
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
            setCategory(event.target.value as SecurityCategory | '')
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
              <EmptyTableRow colSpan={7}>No security findings found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
