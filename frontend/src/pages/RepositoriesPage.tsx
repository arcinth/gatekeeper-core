import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { repositoryService } from '../services/repositoryService'
import type { Repository } from '../types/repository'

const ACTIVE_STYLES: Record<'active' | 'inactive', string> = {
  active: 'bg-emerald-100 text-emerald-800',
  inactive: 'bg-slate-100 text-slate-600',
}

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
    <AppLayout>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Repositories</h1>

      {error && <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Repository</th>
                <th className="px-4 py-2">Description</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Connected</th>
                <th className="px-4 py-2">Governance</th>
                <th className="px-4 py-2"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {repositories.length ? (
                repositories.map((repository) => (
                  <tr key={repository.id} className="hover:bg-slate-50">
                    <td className="px-4 py-2 font-medium text-slate-900">{repository.fullName}</td>
                    <td className="px-4 py-2 text-slate-500">{repository.description ?? '—'}</td>
                    <td className="px-4 py-2">
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                          repository.active ? ACTIVE_STYLES.active : ACTIVE_STYLES.inactive
                        }`}
                      >
                        {repository.active ? 'Active' : 'Inactive'}
                      </span>
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
                      <button
                        onClick={() => void handleRemove(repository)}
                        disabled={removingId === repository.id}
                        className="rounded-md border border-red-300 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
                      >
                        {removingId === repository.id ? 'Removing...' : 'Remove'}
                      </button>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                    No repositories connected.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </AppLayout>
  )
}
