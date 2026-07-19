import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { ACTIVE_STATE_TONES } from '../components/ui/badgeTones'
import { repositoryService } from '../services/repositoryService'
import type { Repository } from '../types/repository'

export function RepositoriesPage() {
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [removingId, setRemovingId] = useState<number | null>(null)

  useEffect(() => {
    loadRepositories()
  }, [])

  function loadRepositories() {
    setIsLoading(true)
    setError(null)
    repositoryService
      .list()
      .then(setRepositories)
      .catch(() => setError('Failed to load repositories.'))
      .finally(() => setIsLoading(false))
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
    <AppLayout title="Repositories">
      {error && <ErrorState message={error} />}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
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
              <EmptyTableRow colSpan={6}>No repositories connected.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}
