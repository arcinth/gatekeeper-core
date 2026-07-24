import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { Cable, ExternalLink, FolderGit2, RefreshCw } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface, SectionHeading } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows, SkeletonTiles } from '../components/ui/Skeleton'
import { buttonClasses } from '../components/ui/buttonClasses'
import { installationStatusTone } from '../components/ui/tones'
import { formatRelativeTime } from '../lib/format'
import { githubInstallationService } from '../services/githubInstallationService'
import { repositoryService } from '../services/repositoryService'
import type { ApiErrorResponse } from '../types/api'
import type { GitHubInstallation } from '../types/githubInstallation'
import type { Repository } from '../types/repository'

const STATUS_LABELS: Record<GitHubInstallation['status'], string> = {
  CONNECTING: 'Connecting',
  SYNCING: 'Syncing',
  ACTIVE: 'Active',
  ERROR: 'Sync failed',
  DISCONNECTED: 'Disconnected',
}

/**
 * The operator home (Product Experience spec, §07). Connection health leads,
 * because a broken installation silently starves every downstream screen -
 * then the repositories themselves, each one a link into its own governance
 * detail rather than a separate top-level "Governance" destination.
 */
export function RepositoriesPage() {
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [installations, setInstallations] = useState<GitHubInstallation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<number | null>(null)
  const [syncingId, setSyncingId] = useState<number | null>(null)
  const [isConnecting, setIsConnecting] = useState(false)

  const loadAll = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [repositoriesResult, installationsResult] = await Promise.all([
        repositoryService.list(),
        githubInstallationService.list(),
      ])
      setRepositories(repositoriesResult)
      setInstallations(installationsResult)
    } catch {
      setError('Failed to load repositories.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadAll()
  }, [loadAll])

  async function handleConnectGitHub() {
    setError(null)
    setIsConnecting(true)
    try {
      const { url, appConfigured } = await githubInstallationService.getInstallUrl()
      if (!appConfigured || !url) {
        setError('The GitHub App is not configured for this GateKeeper instance. Contact your administrator.')
        return
      }
      window.location.href = url
    } catch (err) {
      setError(describeError(err, 'connect GitHub'))
    } finally {
      setIsConnecting(false)
    }
  }

  async function handleResync(installation: GitHubInstallation) {
    setError(null)
    setSyncingId(installation.id)
    try {
      const updated = await githubInstallationService.sync(installation.id)
      setInstallations((current) => current.map((existing) => (existing.id === updated.id ? updated : existing)))
      repositoryService
        .list()
        .then(setRepositories)
        .catch(() => undefined)
    } catch (err) {
      setError(describeError(err, 'resynchronize this installation'))
    } finally {
      setSyncingId(null)
    }
  }

  async function handleRemove(repository: Repository) {
    if (!window.confirm(`Disconnect ${repository.fullName}? GateKeeper will no longer analyze its pull requests.`)) {
      return
    }
    setError(null)
    setRemovingId(repository.id)
    try {
      await repositoryService.remove(repository.id)
      setRepositories((current) => current.filter((existing) => existing.id !== repository.id))
    } catch {
      // The backend's own @PreAuthorize is the real guard - a 403 here is
      // reported rather than silently swallowed.
      setError(`Failed to disconnect ${repository.fullName}. You may not have permission to do this.`)
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <AppLayout
      eyebrow="Operations"
      title="Repositories"
      description="Connection health and the repositories GateKeeper governs."
      actions={
        <button
          type="button"
          className={buttonClasses('primary', 'md')}
          onClick={() => void handleConnectGitHub()}
          disabled={isConnecting}
        >
          <Cable className="h-4 w-4" aria-hidden="true" />
          {isConnecting ? 'Opening GitHub…' : 'Connect GitHub'}
        </button>
      }
    >
      {error && <ErrorState message={error} onRetry={() => void loadAll()} className="mb-5" />}

      <div className="flex flex-col gap-7">
        <section>
          <SectionHeading eyebrow="Connections" title="GitHub" />
          {isLoading ? (
            <SkeletonTiles count={3} />
          ) : installations.length === 0 ? (
            <EmptyState
              icon={Cable}
              title="No GitHub connections yet"
              description="Connect a GitHub organization or account so GateKeeper can discover and analyze its repositories."
              action={
                <button
                  type="button"
                  className={buttonClasses('primary', 'md')}
                  onClick={() => void handleConnectGitHub()}
                  disabled={isConnecting}
                >
                  Connect GitHub
                </button>
              }
            />
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {installations.map((installation) => (
                <InstallationCard
                  key={installation.id}
                  installation={installation}
                  isSyncing={syncingId === installation.id}
                  onResync={() => void handleResync(installation)}
                />
              ))}
            </div>
          )}
        </section>

        <section>
          <SectionHeading
            eyebrow="Governed"
            title="Repositories"
            actions={
              <span className="tabular font-mono text-[11px] text-faint">
                {repositories.length} connected
              </span>
            }
          />
          {isLoading ? (
            <SkeletonRows rows={4} />
          ) : repositories.length === 0 ? (
            <EmptyState
              icon={FolderGit2}
              title="No repositories connected yet"
              description="Once a GitHub connection finishes syncing, its repositories appear here automatically."
            />
          ) : (
            <div className="divide-y divide-line-soft overflow-hidden rounded-lg border border-line bg-surface">
              {repositories.map((repository) => (
                <div key={repository.id} className="flex items-center gap-4 px-4 py-3.5">
                  <Link to={`/repositories/${repository.id}`} className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-content">{repository.fullName}</p>
                    <p className="mt-0.5 truncate text-xs text-muted">
                      {repository.description ?? 'No description'}
                    </p>
                  </Link>

                  <Chip tone={repository.active ? 'pass' : 'neutral'}>
                    {repository.active ? 'Active' : 'Inactive'}
                  </Chip>

                  <span className="hidden shrink-0 font-mono text-[11px] text-faint md:block">
                    {formatRelativeTime(repository.createdAt)}
                  </span>

                  <button
                    type="button"
                    className={buttonClasses('danger', 'sm')}
                    onClick={() => void handleRemove(repository)}
                    disabled={removingId === repository.id}
                  >
                    {removingId === repository.id ? 'Removing…' : 'Disconnect'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppLayout>
  )
}

function InstallationCard({
  installation,
  isSyncing,
  onResync,
}: {
  installation: GitHubInstallation
  isSyncing: boolean
  onResync: () => void
}) {
  return (
    <Surface padding="p-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-content">{installation.githubAccountLogin}</p>
          <p className="mt-0.5 truncate font-mono text-[11px] text-faint">
            {installation.githubAccountType ?? 'Account'} · {installation.repositorySelection ?? 'unknown'} repos
          </p>
        </div>
        <Chip tone={installationStatusTone(installation.status)}>{STATUS_LABELS[installation.status]}</Chip>
      </div>

      <dl className="mt-3.5 flex flex-col gap-1.5 text-xs">
        <div className="flex justify-between gap-2">
          <dt className="text-faint">Repositories</dt>
          <dd className="tabular text-muted">{installation.repositoryCount}</dd>
        </div>
        <div className="flex justify-between gap-2">
          <dt className="text-faint">Last sync</dt>
          <dd className="text-muted">
            {installation.lastSuccessfulSyncAt ? formatRelativeTime(installation.lastSuccessfulSyncAt) : 'Never'}
          </dd>
        </div>
      </dl>

      {installation.status === 'ERROR' && installation.lastSyncError && (
        <p className="mt-3 rounded-md border border-block-line bg-block-bg px-2.5 py-2 text-xs text-muted">
          {installation.lastSyncError}
        </p>
      )}

      <div className="mt-4 flex items-center justify-between gap-2">
        <a
          href={`https://github.com/settings/installations/${installation.installationId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1.5 font-mono text-[11px] text-faint transition-colors hover:text-content"
        >
          Manage
          <ExternalLink className="h-3 w-3" aria-hidden="true" />
        </a>
        <button type="button" className={buttonClasses('secondary', 'sm')} onClick={onResync} disabled={isSyncing}>
          <RefreshCw className={`h-3.5 w-3.5 ${isSyncing ? 'animate-spin' : ''}`} aria-hidden="true" />
          {isSyncing ? 'Syncing…' : 'Resync'}
        </button>
      </div>
    </Surface>
  )
}

function describeError(err: unknown, action: string): string {
  if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
    if (err.response.status === 403) {
      return `You do not have permission to ${action}.`
    }
    return err.response.data.error.message
  }
  return `Failed to ${action}.`
}
