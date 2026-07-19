import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { AI_REVIEW_RUN_STATUS_TONES } from '../components/ui/badgeTones'
import { aiReviewRunService } from '../services/aiReviewRunService'
import { repositoryService } from '../services/repositoryService'
import type { AIReviewRunStatus, AIReviewRunSummary } from '../types/aiReviewRun'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const STATUS_OPTIONS: AIReviewRunStatus[] = ['COMPLETED', 'FAILED']
const PAGE_SIZE = 20

export function AIReviewRunsPage() {
  const [page, setPage] = useState<PageResponse<AIReviewRunSummary> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [status, setStatus] = useState<AIReviewRunStatus | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    aiReviewRunService
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
    <AppLayout
      title="AI Review Runs"
      eyebrow={
        <Badge tone="bg-violet-100 text-violet-700" uppercase>
          Advisory &middot; AI Generated
        </Badge>
      }
    >
      <div className="mb-4 flex flex-wrap gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={status}
          onChange={(event) => {
            setPageNumber(0)
            setStatus(event.target.value as AIReviewRunStatus | '')
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
          tone="ai"
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
          <TableHead tone="ai">
            <tr>
              <th className="px-4 py-2">Repository</th>
              <th className="px-4 py-2">Pull Request</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Provider</th>
              <th className="px-4 py-2">Model</th>
              <th className="px-4 py-2">Findings</th>
              <th className="px-4 py-2">Created</th>
            </tr>
          </TableHead>
          <TableBody>
            {page?.content.length ? (
              page.content.map((run) => (
                <tr key={run.id} className="hover:bg-violet-50/50">
                  <td className="px-4 py-2 text-slate-700">{run.repositoryFullName}</td>
                  <td className="px-4 py-2">
                    <Link to={`/ai-review-runs/${run.id}`} className="text-violet-700 hover:underline">
                      #{run.pullRequestNumber}
                    </Link>
                  </td>
                  <td className="px-4 py-2">
                    <Badge tone={AI_REVIEW_RUN_STATUS_TONES[run.status]}>{run.status}</Badge>
                  </td>
                  <td className="px-4 py-2 text-slate-500">{run.provider}</td>
                  <td className="px-4 py-2 text-slate-500">{run.model}</td>
                  <td className="px-4 py-2 text-slate-700 tabular-nums">{run.findingsTotal}</td>
                  <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
                </tr>
              ))
            ) : (
              <EmptyTableRow colSpan={7}>No AI review runs found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
