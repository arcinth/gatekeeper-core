import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { VERDICT_OUTCOME_TONES } from '../components/ui/badgeTones'
import { verdictService } from '../services/verdictService'
import { repositoryService } from '../services/repositoryService'
import type { VerdictOutcome, VerdictSummary } from '../types/verdict'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'

const OUTCOME_OPTIONS: VerdictOutcome[] = ['APPROVED', 'BLOCKED']
const PAGE_SIZE = 20

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
    <AppLayout
      title="Verdicts"
      eyebrow={
        <Badge tone="bg-slate-800 text-white" uppercase>
          Governance Decision
        </Badge>
      }
    >
      <div className="mb-4 flex flex-wrap gap-3">
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
              <th className="px-4 py-2">Outcome</th>
              <th className="px-4 py-2">Reasons</th>
              <th className="px-4 py-2">Decided</th>
            </tr>
          </TableHead>
          <TableBody>
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
                    <Badge tone={VERDICT_OUTCOME_TONES[verdict.outcome]} bold>
                      {verdict.outcome}
                    </Badge>
                  </td>
                  <td className="px-4 py-2 text-slate-700 tabular-nums">{verdict.reasonsTotal}</td>
                  <td className="px-4 py-2 text-slate-500">{new Date(verdict.createdAt).toLocaleString()}</td>
                </tr>
              ))
            ) : (
              <EmptyTableRow colSpan={5}>No verdicts found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
