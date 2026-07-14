import { useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { dashboardService } from '../services/dashboardService'
import type { DashboardStatus } from '../types/dashboard'

export function DashboardPage() {
  const [status, setStatus] = useState<DashboardStatus | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    dashboardService
      .getStatus()
      .then(setStatus)
      .finally(() => setIsLoading(false))
  }, [])

  return (
    <AppLayout>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">Dashboard</h1>
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
          <p className="text-sm text-slate-500">Platform status</p>
          <p className="mt-1 text-lg font-medium text-slate-900">{status?.status ?? 'unknown'}</p>
          <p className="mt-4 text-sm text-slate-500">Version</p>
          <p className="mt-1 text-lg font-medium text-slate-900">{status?.version ?? 'unknown'}</p>
        </div>
      )}
    </AppLayout>
  )
}
