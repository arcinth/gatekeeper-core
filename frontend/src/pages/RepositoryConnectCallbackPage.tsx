import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { ErrorState } from '../components/ui/states'
import { Skeleton } from '../components/ui/Skeleton'
import { githubInstallationService } from '../services/githubInstallationService'

/**
 * Where GitHub redirects after the user completes (or cancels) the App
 * installation flow, with the installation id already in the URL. Reconciles
 * it directly (GET the installation from GitHub and upsert it) rather than
 * waiting on the "installation" webhook: GitHub cannot deliver that webhook
 * to a local backend at all, so waiting on it here would never resolve in
 * development. A real deployment's webhook still arrives independently and
 * is a no-op against the same, already-reconciled row.
 */
export function RepositoryConnectCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'connecting' | 'syncing' | 'error'>('connecting')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const cancelled = useRef(false)

  const installationIdParam = searchParams.get('installation_id')
  const setupAction = searchParams.get('setup_action')

  useEffect(() => {
    cancelled.current = false

    if (setupAction === 'request') {
      // Still awaiting an organization owner's approval on GitHub's side -
      // nothing for GateKeeper to reconcile yet.
      navigate('/repositories', { replace: true })
      return
    }

    const installationId = installationIdParam ? Number(installationIdParam) : null
    if (!installationId) {
      setStatus('error')
      setErrorMessage('This link is missing the information GitHub normally provides. Return to Repositories and try connecting again.')
      return
    }

    void reconcileAndSync(installationId)

    return () => {
      cancelled.current = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function reconcileAndSync(installationId: number) {
    setStatus('connecting')
    setErrorMessage(null)
    try {
      const installation = await githubInstallationService.reconcile(installationId)
      if (cancelled.current) return

      setStatus('syncing')
      try {
        await githubInstallationService.sync(installation.id)
      } catch {
        // The row is already correctly linked at this point - a sync hiccup
        // just means repositories catch up on the next resync, not a reason
        // to block the redirect.
      }
      if (!cancelled.current) {
        navigate('/repositories', { replace: true })
      }
    } catch {
      if (!cancelled.current) {
        setStatus('error')
        setErrorMessage('GateKeeper could not reach GitHub to confirm this connection. Check the Repositories page, or try connecting again.')
      }
    }
  }

  return (
    <AppLayout
      width="narrow"
      eyebrow="Repository onboarding"
      title="Connecting GitHub"
      breadcrumbs={[{ label: 'Repositories', to: '/repositories' }, { label: 'Connecting' }]}
    >
      {status === 'error' && (
        <ErrorState
          message={errorMessage ?? 'Something went wrong connecting to GitHub.'}
          onRetry={
            installationIdParam ? () => void reconcileAndSync(Number(installationIdParam)) : undefined
          }
        />
      )}
      {(status === 'connecting' || status === 'syncing') && (
        <div className="rounded-lg border border-line bg-surface p-6">
          <p className="text-sm text-muted">
            {status === 'connecting' ? 'Finishing your GitHub connection…' : 'Synchronizing repositories…'}
          </p>
          <div className="mt-4 flex flex-col gap-2.5">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-4/5" />
            <Skeleton className="h-4 w-2/3" />
          </div>
        </div>
      )}
    </AppLayout>
  )
}
