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
          <Link
            to="/ai-review-runs"
            className="rounded-md border border-violet-300 px-3 py-1.5 text-sm font-medium text-violet-700 hover:bg-violet-50"
          >
            View AI Review Runs
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

          <div className="mb-3 mt-8 flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900">AI Review</h2>
            <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-violet-700">
              Advisory &middot; AI Generated
            </span>
          </div>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard
              label={`AI Review Runs (${overview?.windowDays ?? 30}d)`}
              value={overview?.totalAiReviewRuns ?? 0}
              tone="ai"
            />
            <StatCard
              label="Failed Reviews"
              value={overview?.aiReviewRunsByStatus?.FAILED ?? 0}
              tone="ai-alert"
            />
            <StatCard
              label={`AI Findings (${overview?.windowDays ?? 30}d)`}
              value={overview?.totalAiReviewFindings ?? 0}
              tone="ai"
            />
            <StatCard
              label="High Confidence"
              value={overview?.aiReviewFindingsByConfidence?.HIGH ?? 0}
              tone="ai"
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
  tone?: 'critical' | 'high' | 'ai' | 'ai-alert'
}) {
  const valueClassName =
    tone === 'critical' && value > 0
      ? 'text-red-700'
      : tone === 'high' && value > 0
        ? 'text-orange-700'
        : tone === 'ai-alert' && value > 0
          ? 'text-red-700'
          : tone === 'ai'
            ? 'text-violet-700'
            : 'text-slate-900'

  // AI-toned cards get a violet border, visually distinguishing them from the
  // deterministic (Policy/Security) cards above (Sprint 4 Milestone 4).
  const borderClassName = tone === 'ai' || tone === 'ai-alert' ? 'border-violet-200' : 'border-slate-200'

  return (
    <div className={`rounded-lg border ${borderClassName} bg-white p-6 shadow-sm`}>
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${valueClassName}`}>{value}</p>
    </div>
  )
}
