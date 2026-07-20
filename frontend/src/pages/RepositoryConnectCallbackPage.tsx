import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { ErrorState } from '../components/ui/ErrorState'
import { githubInstallationService } from '../services/githubInstallationService'

const POLL_INTERVAL_MS = 1500
const MAX_POLL_ATTEMPTS = 15

/**
 * Where GitHub redirects the browser after the user completes (or cancels)
 * the App installation flow (Milestone 8: Repository Onboarding) - configured
 * as the App's "Setup URL" in GitHub's own settings, an operator action, not
 * code. GitHub appends `installation_id`/`setup_action` query params; the
 * webhook that actually persists the GitHubInstallation row is delivered
 * independently and may not have arrived yet, so this page polls the
 * installations list briefly rather than assuming it's already there, then
 * triggers an immediate synchronous resync (the same GitHubRepositorySyncService
 * logic the webhook path already runs asynchronously) purely to avoid making
 * the user wait on that async round trip before their repositories appear.
 */
export function RepositoryConnectCallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [status, setStatus] = useState<'waiting' | 'syncing' | 'timedOut' | 'error'>('waiting')
  const cancelled = useRef(false)

  const installationIdParam = searchParams.get('installation_id')
  const setupAction = searchParams.get('setup_action')

  useEffect(() => {
    cancelled.current = false

    if (setupAction === 'request') {
      // The installation still needs an organization owner's approval on GitHub's
      // side - nothing for GateKeeper to reconcile yet.
      navigate('/repositories', { replace: true })
      return
    }

    const installationId = installationIdParam ? Number(installationIdParam) : null
    if (!installationId) {
      setStatus('error')
      return
    }

    void pollForInstallation(installationId)

    return () => {
      cancelled.current = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function pollForInstallation(installationId: number) {
    for (let attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
      if (cancelled.current) return
      try {
        const installations = await githubInstallationService.list()
        const match = installations.find((installation) => installation.installationId === installationId)
        if (match) {
          setStatus('syncing')
          try {
            await githubInstallationService.sync(match.id)
          } catch {
            // The webhook-driven sync will still complete in the background even if
            // this synchronous nudge fails - not a reason to block the redirect.
          }
          if (!cancelled.current) {
            navigate('/repositories', { replace: true })
          }
          return
        }
      } catch {
        // Transient read failure - keep polling until MAX_POLL_ATTEMPTS is exhausted.
      }
      await sleep(POLL_INTERVAL_MS)
    }
    if (!cancelled.current) {
      setStatus('timedOut')
    }
  }

  return (
    <AppLayout title="Connecting GitHub" eyebrow="Repository Onboarding">
      {status === 'error' && (
        <ErrorState message="This link is missing the information GitHub normally provides. Return to Repositories and try connecting again." />
      )}
      {status === 'timedOut' && (
        <ErrorState message="Still waiting to hear from GitHub. This can take a moment - check the Repositories page shortly; the connection will appear once GitHub's notification arrives." />
      )}
      {(status === 'waiting' || status === 'syncing') && (
        <div className="flex flex-col items-center gap-3 py-8">
          <LoadingSpinner />
          <p className="text-sm text-slate-500">
            {status === 'waiting' ? 'Finishing your GitHub connection...' : 'Synchronizing repositories...'}
          </p>
        </div>
      )}
    </AppLayout>
  )
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
