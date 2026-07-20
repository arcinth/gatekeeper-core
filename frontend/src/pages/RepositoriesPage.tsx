import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { EmptyState } from '../components/ui/EmptyState'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { ACTIVE_STATE_TONES, GITHUB_INSTALLATION_STATUS_TONES } from '../components/ui/badgeTones'
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

export function RepositoriesPage() {
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [installations, setInstallations] = useState<GitHubInstallation[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<number | null>(null)
  const [syncingId, setSyncingId] = useState<number | null>(null)
  const [isConnecting, setIsConnecting] = useState(false)

  useEffect(() => {
    loadAll()
  }, [])

  function loadAll() {
    setIsLoading(true)
    setError(null)
    Promise.all([repositoryService.list(), githubInstallationService.list()])
      .then(([repositoriesResult, installationsResult]) => {
        setRepositories(repositoriesResult)
        setInstallations(installationsResult)
      })
      .catch(() => setError('Failed to load repositories.'))
      .finally(() => setIsLoading(false))
  }

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
      repositoryService.list().then(setRepositories).catch(() => undefined)
    } catch (err) {
      setError(describeError(err, 'resynchronize this installation'))
    } finally {
      setSyncingId(null)
    }
  }

  async function handleRemove(repository: Repository) {
    if (!window.confirm(`Disconnect ${repository.fullName}? GateKeeper will no longer analyze its Pull Requests.`)) {
      return
    }
    setError(null)
    setRemovingId(repository.id)
    try {
      await repositoryService.remove(repository.id)
      setRepositories((current) => current.filter((existing) => existing.id !== repository.id))
    } catch {
      // No frontend role-gating exists anywhere else in this app either - the
      // backend's own @PreAuthorize (ADMINISTRATOR/PLATFORM_ENGINEER) is the
      // real guard; a 403 here is reported, not silently swallowed.
      setError(`Failed to disconnect ${repository.fullName}. You may not have permission to do this.`)
    } finally {
      setRemovingId(null)
    }
  }

  return (
    <AppLayout
      title="Repositories"
      description="Connect GitHub repositories for GateKeeper to govern, and manage the ones already connected."
      actions={
        <Button variant="primary" onClick={() => void handleConnectGitHub()} disabled={isConnecting}>
          {isConnecting ? 'Opening GitHub...' : 'Connect GitHub'}
        </Button>
      }
    >
      {error && <ErrorState message={error} />}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <section className="mb-8">
            <h2 className="mb-3 text-sm font-semibold text-slate-700">GitHub Connections</h2>
            {installations.length ? (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {installations.map((installation) => (
                  <InstallationCard
                    key={installation.id}
                    installation={installation}
                    isSyncing={syncingId === installation.id}
                    onResync={() => void handleResync(installation)}
                  />
                ))}
              </div>
            ) : (
              <EmptyState
                title="No GitHub connections yet"
                description="Connect a GitHub organization or account so GateKeeper can discover and analyze its repositories."
              />
            )}
          </section>

          <section>
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Repositories</h2>
            <Table>
              <TableHead>
                <tr>
                  <th className="px-4 py-2">Repository</th>
                  <th className="px-4 py-2">Description</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2">Connected</th>
                  <th className="px-4 py-2">Governance</th>
                  <th className="px-4 py-2"></th>
                </tr>
              </TableHead>
              <TableBody>
                {repositories.length ? (
                  repositories.map((repository) => (
                    <tr key={repository.id} className="hover:bg-slate-50">
                      <td className="px-4 py-2 font-medium text-slate-900">{repository.fullName}</td>
                      <td className="px-4 py-2 text-slate-500">{repository.description ?? '—'}</td>
                      <td className="px-4 py-2">
                        <Badge tone={repository.active ? ACTIVE_STATE_TONES.active : ACTIVE_STATE_TONES.inactive}>
                          {repository.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="px-4 py-2 text-slate-500">{new Date(repository.createdAt).toLocaleString()}</td>
                      <td className="px-4 py-2">
                        <Link
                          to={`/repositories/${repository.id}/governance`}
                          className="text-xs font-medium text-slate-700 hover:underline"
                        >
                          View Governance &rarr;
                        </Link>
                      </td>
                      <td className="px-4 py-2 text-right">
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => void handleRemove(repository)}
                          disabled={removingId === repository.id}
                        >
                          {removingId === repository.id ? 'Removing...' : 'Remove'}
                        </Button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <EmptyTableRow colSpan={6}>
                    No repositories connected yet - connect a GitHub account above to get started.
                  </EmptyTableRow>
                )}
              </TableBody>
            </Table>
          </section>
        </>
      )}
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
    <Card padding="p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-semibold text-slate-900">{installation.githubAccountLogin}</p>
          <p className="text-xs text-slate-500">
            {installation.githubAccountType ?? 'Account'} &middot; {installation.repositorySelection ?? 'unknown'} repositories
          </p>
        </div>
        <Badge tone={GITHUB_INSTALLATION_STATUS_TONES[installation.status]}>{STATUS_LABELS[installation.status]}</Badge>
      </div>

      <div className="mt-3 text-xs text-slate-500">
        <p>{installation.repositoryCount} repositor{installation.repositoryCount === 1 ? 'y' : 'ies'} connected</p>
        <p>Last synchronized: {formatRelativeTime(installation.lastSuccessfulSyncAt)}</p>
        {installation.status === 'ERROR' && installation.lastSyncError && (
          <p className="mt-1 rounded border border-red-200 bg-red-50 px-2 py-1 text-red-700">
            {installation.lastSyncError}
          </p>
        )}
      </div>

      <div className="mt-3 flex items-center justify-between">
        <a
          href={`https://github.com/settings/installations/${installation.installationId}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs font-medium text-slate-500 hover:underline"
        >
          Manage on GitHub &rarr;
        </a>
        <Button variant="secondary" size="sm" onClick={onResync} disabled={isSyncing}>
          {isSyncing ? 'Syncing...' : 'Resync now'}
        </Button>
      </div>
    </Card>
  )
}

function formatRelativeTime(isoTimestamp: string | null): string {
  if (!isoTimestamp) {
    return 'never'
  }
  const elapsedMs = Date.now() - new Date(isoTimestamp).getTime()
  const elapsedMinutes = Math.round(elapsedMs / 60_000)
  if (elapsedMinutes < 1) {
    return 'just now'
  }
  if (elapsedMinutes < 60) {
    return `${elapsedMinutes} minute${elapsedMinutes === 1 ? '' : 's'} ago`
  }
  const elapsedHours = Math.round(elapsedMinutes / 60)
  if (elapsedHours < 24) {
    return `${elapsedHours} hour${elapsedHours === 1 ? '' : 's'} ago`
  }
  const elapsedDays = Math.round(elapsedHours / 24)
  return `${elapsedDays} day${elapsedDays === 1 ? '' : 's'} ago`
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
