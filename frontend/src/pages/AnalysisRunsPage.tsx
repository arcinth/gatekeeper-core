import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { ANALYSIS_RUN_STATUS_TONES, VERDICT_OUTCOME_TONES } from '../components/ui/badgeTones'
import { analysisRunService } from '../services/analysisRunService'
import { repositoryService } from '../services/repositoryService'
import type { AnalysisRunStatus, AnalysisRunSummary } from '../types/analysisRun'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const STATUS_OPTIONS: AnalysisRunStatus[] = ['RECEIVED', 'QUEUED', 'IN_PROGRESS', 'COMPLETED', 'FAILED']
const PAGE_SIZE = 20

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
    <AppLayout title="Analysis Runs">
      <div className="mb-4 flex flex-wrap gap-3">
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
              <th className="px-4 py-2">Repository</th>
              <th className="px-4 py-2">Pull Request</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Verdict</th>
              <th className="px-4 py-2">Findings</th>
              <th className="px-4 py-2">Created</th>
            </tr>
          </TableHead>
          <TableBody>
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
                    <Badge tone={ANALYSIS_RUN_STATUS_TONES[run.status]}>{run.status}</Badge>
                  </td>
                  <td className="px-4 py-2">
                    {run.verdictOutcome ? (
                      <Link to={`/analysis-runs/${run.id}`}>
                        <Badge tone={VERDICT_OUTCOME_TONES[run.verdictOutcome]} bold>
                          {run.verdictOutcome}
                        </Badge>
                      </Link>
                    ) : (
                      <span className="text-slate-400">&mdash;</span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-slate-700 tabular-nums">{run.findingsTotal}</td>
                  <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
                </tr>
              ))
            ) : (
              <EmptyTableRow colSpan={6}>No analysis runs found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
