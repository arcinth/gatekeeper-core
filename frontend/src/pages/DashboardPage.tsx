import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { dashboardService } from '../services/dashboardService'
import type { DashboardOverview } from '../types/dashboard'

export function DashboardPage() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    dashboardService
      .getOverview()
      .then(setOverview)
      .finally(() => setIsLoading(false))
  }, [])

  return (
    <AppLayout>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Dashboard</h1>
        <div className="flex gap-2">
          <Link
            to="/analysis-runs"
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            View Analysis Runs
          </Link>
          <Link
            to="/security-findings"
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            View Security Findings
          </Link>
        </div>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Repositories" value={overview?.totalRepositories ?? 0} />
            <StatCard label={`Analysis Runs (${overview?.windowDays ?? 30}d)`} value={overview?.totalAnalysisRuns ?? 0} />
            <StatCard label={`Policy Findings (${overview?.windowDays ?? 30}d)`} value={overview?.totalFindings ?? 0} />
            <StatCard label="Failed Runs" value={overview?.runsByStatus?.FAILED ?? 0} />
          </div>

          <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Security</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard
              label={`Security Findings (${overview?.windowDays ?? 30}d)`}
              value={overview?.totalSecurityFindings ?? 0}
            />
            <StatCard
              label="Critical"
              value={overview?.securityFindingsBySeverity?.CRITICAL ?? 0}
              tone="critical"
            />
            <StatCard label="High" value={overview?.securityFindingsBySeverity?.HIGH ?? 0} tone="high" />
            <StatCard
              label="Secrets Exposure"
              value={overview?.securityFindingsByCategory?.SECRETS_EXPOSURE ?? 0}
            />
          </div>
        </>
      )}
    </AppLayout>
  )
}

function StatCard({
  label,
  value,
  tone,
}: {
  label: string
  value: number
  tone?: 'critical' | 'high'
}) {
  const valueClassName =
    tone === 'critical' && value > 0
      ? 'text-red-700'
      : tone === 'high' && value > 0
        ? 'text-orange-700'
        : 'text-slate-900'

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${valueClassName}`}>{value}</p>
    </div>
  )
}
