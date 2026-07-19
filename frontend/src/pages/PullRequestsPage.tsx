import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { ANALYSIS_RUN_STATUS_TONES, PULL_REQUEST_STATUS_TONES, VERDICT_OUTCOME_TONES } from '../components/ui/badgeTones'
import { pullRequestService } from '../services/pullRequestService'
import { repositoryService } from '../services/repositoryService'
import type { PullRequestStatus } from '../types/analysisRun'
import type { PageResponse } from '../types/api'
import type { PullRequestSummary } from '../types/pullRequest'
import type { Repository } from '../types/repository'

const STATUS_OPTIONS: PullRequestStatus[] = ['OPEN', 'CLOSED', 'MERGED']
const PAGE_SIZE = 20

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
    <AppLayout title="Pull Requests" description="The reviewer's primary workspace.">
      <div className="mb-4 flex flex-wrap gap-3">
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

      {error && <ErrorState message={error} />}

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
              <th className="px-4 py-2">Pull Request</th>
              <th className="px-4 py-2">Repository</th>
              <th className="px-4 py-2">Author</th>
              <th className="px-4 py-2">Branch</th>
              <th className="px-4 py-2">Latest Analysis</th>
              <th className="px-4 py-2">Verdict</th>
              <th className="px-4 py-2">Last Updated</th>
              <th className="px-4 py-2">GitHub</th>
            </tr>
          </TableHead>
          <TableBody>
            {page?.content.length ? (
              page.content.map((pr) => (
                <tr key={pr.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2">
                    <Link to={`/pull-requests/${pr.id}`} className="text-slate-900 hover:underline">
                      #{pr.number} {pr.title}
                    </Link>
                    <Badge tone={PULL_REQUEST_STATUS_TONES[pr.status]} className="ml-2">
                      {pr.status}
                    </Badge>
                  </td>
                  <td className="px-4 py-2 text-slate-700">{pr.repositoryFullName}</td>
                  <td className="px-4 py-2 text-slate-700">{pr.authorLogin}</td>
                  <td className="px-4 py-2 text-slate-500">
                    {pr.sourceBranch} &rarr; {pr.targetBranch}
                  </td>
                  <td className="px-4 py-2">
                    {pr.latestAnalysisRunStatus ? (
                      <Link to={`/analysis-runs/${pr.latestAnalysisRunId}`}>
                        <Badge tone={ANALYSIS_RUN_STATUS_TONES[pr.latestAnalysisRunStatus]}>
                          {pr.latestAnalysisRunStatus}
                        </Badge>
                      </Link>
                    ) : (
                      <span className="text-slate-400">&mdash;</span>
                    )}
                  </td>
                  <td className="px-4 py-2">
                    {pr.latestVerdictOutcome ? (
                      <Link to={`/analysis-runs/${pr.latestAnalysisRunId}`}>
                        <Badge tone={VERDICT_OUTCOME_TONES[pr.latestVerdictOutcome]} bold>
                          {pr.latestVerdictOutcome}
                        </Badge>
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
              <EmptyTableRow colSpan={8}>No pull requests found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
