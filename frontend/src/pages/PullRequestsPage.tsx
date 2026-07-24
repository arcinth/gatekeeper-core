import { useCallback, useEffect, useMemo, useState } from 'react'
import { GitPullRequest } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Label, Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { PullRequestRow } from '../components/domain/PullRequestRow'
import { pullRequestService } from '../services/pullRequestService'
import { repositoryService } from '../services/repositoryService'
import type { PullRequestStatus } from '../types/analysisRun'
import type { PullRequestSummary } from '../types/pullRequest'
import type { Repository } from '../types/repository'
import type { PageResponse } from '../types/api'

type SavedView = 'all' | 'blocked' | 'cleared' | 'running'

const SAVED_VIEWS: { id: SavedView; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'blocked', label: 'Blocked' },
  { id: 'cleared', label: 'Cleared' },
  { id: 'running', label: 'In progress' },
]

/**
 * The reviewer workspace, promoted from "one list among five entity lists" to
 * a first-class triage surface (Product Experience spec, §07). Saved views
 * lead, since "show me what's blocked" is the question people actually arrive
 * with - the raw status dropdown is now a secondary filter rather than the
 * primary way in.
 */
export function PullRequestsPage() {
  const [page, setPage] = useState<PageResponse<PullRequestSummary> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [view, setView] = useState<SavedView>('all')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [status, setStatus] = useState<PullRequestStatus | ''>('OPEN')
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService
      .list()
      .then(setRepositories)
      .catch(() => {
        // A failed repository list only costs the filter dropdown - the page
        // still works unfiltered, so this is not surfaced as a page error.
      })
  }, [])

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setPage(
        await pullRequestService.list({
          page: pageNumber,
          size: 25,
          repositoryId: repositoryId === '' ? undefined : repositoryId,
          status: status === '' ? undefined : status,
        }),
      )
    } catch {
      setError('Failed to load pull requests.')
    } finally {
      setIsLoading(false)
    }
  }, [pageNumber, repositoryId, status])

  useEffect(() => {
    void load()
  }, [load])

  // Saved views filter on verdict/run state, which the list endpoint does not
  // expose as a query parameter - so this narrowing happens client-side over
  // the current page rather than as a server round trip.
  const visible = useMemo(() => {
    const content = page?.content ?? []
    switch (view) {
      case 'blocked':
        return content.filter((pr) => pr.latestVerdictOutcome === 'BLOCKED')
      case 'cleared':
        return content.filter((pr) => pr.latestVerdictOutcome === 'APPROVED')
      case 'running':
        return content.filter(
          (pr) => pr.latestAnalysisRunStatus === 'IN_PROGRESS' || pr.latestAnalysisRunStatus === 'QUEUED',
        )
      default:
        return content
    }
  }, [page, view])

  return (
    <AppLayout
      eyebrow="Reviewer workspace"
      title="Pull Requests"
      description="Every pull request GateKeeper is watching, and where each one stands."
    >
      <div className="mb-5 flex flex-col gap-4">
        <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Saved views">
          {SAVED_VIEWS.map((savedView) => (
            <button
              key={savedView.id}
              type="button"
              role="tab"
              aria-selected={view === savedView.id}
              onClick={() => setView(savedView.id)}
              className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                view === savedView.id
                  ? 'bg-accent-bg text-accent-hi'
                  : 'text-muted hover:bg-surface-2 hover:text-content'
              }`}
            >
              {savedView.label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap gap-3">
          <div className="w-full sm:w-64">
            <Label htmlFor="repository">Repository</Label>
            <Select
              id="repository"
              value={repositoryId}
              onChange={(event) => {
                setRepositoryId(event.target.value === '' ? '' : Number(event.target.value))
                setPageNumber(0)
              }}
            >
              <option value="">All repositories</option>
              {repositories.map((repository) => (
                <option key={repository.id} value={repository.id}>
                  {repository.fullName}
                </option>
              ))}
            </Select>
          </div>
          <div className="w-full sm:w-40">
            <Label htmlFor="status">State</Label>
            <Select
              id="status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as PullRequestStatus | '')
                setPageNumber(0)
              }}
            >
              <option value="">Any state</option>
              <option value="OPEN">Open</option>
              <option value="MERGED">Merged</option>
              <option value="CLOSED">Closed</option>
            </Select>
          </div>
        </div>
      </div>

      {error && <ErrorState message={error} onRetry={() => void load()} className="mb-5" />}

      {isLoading ? (
        <SkeletonRows rows={8} />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={GitPullRequest}
          title="No pull requests match"
          description={
            view === 'all'
              ? 'Nothing here yet. Pull requests appear automatically once GitHub sends their webhooks.'
              : 'Try a different saved view, or clear the filters above.'
          }
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-line bg-surface">
          <div className="divide-y divide-line-soft">
            {visible.map((pullRequest) => (
              <PullRequestRow key={pullRequest.id} pullRequest={pullRequest} />
            ))}
          </div>
          {page && (
            <Pagination
              page={page.page}
              totalPages={page.totalPages}
              totalElements={page.totalElements}
              first={page.first}
              last={page.last}
              onPrevious={() => setPageNumber((current) => Math.max(0, current - 1))}
              onNext={() => setPageNumber((current) => current + 1)}
            />
          )}
        </div>
      )}
    </AppLayout>
  )
}
