import { useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
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
    <AppLayout title="Dashboard">
      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          {/*
            Governance section deliberately comes first, above every engine's own
            stat grid - the Verdict is the platform's headline metric (the reason
            GateKeeper exists), not just another engine's finding count buried
            further down the page (Sprint 5 Architecture, Section 15).
          */}
          <div className="mb-3 flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900">Governance</h2>
            <Badge tone="bg-slate-800 text-white" uppercase>
              Verdicts
            </Badge>
          </div>
          <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label={`Verdicts (${overview?.windowDays ?? 30}d)`} value={overview?.totalVerdicts ?? 0} />
            <StatCard label="Approved" value={overview?.verdictsByOutcome?.APPROVED ?? 0} tone="approved" />
            <StatCard label="Blocked" value={overview?.verdictsByOutcome?.BLOCKED ?? 0} tone="blocked" />
            <StatCard
              label="Block Rate"
              value={blockRatePercent(overview?.totalVerdicts, overview?.verdictsByOutcome?.BLOCKED)}
              suffix="%"
              tone={
                blockRatePercent(overview?.totalVerdicts, overview?.verdictsByOutcome?.BLOCKED) > 0
                  ? 'blocked'
                  : undefined
              }
            />
          </div>

          {/*
            Reports Published is a distinct fact from a produced Verdict (a report
            can lag its Verdict while it waits on AI review - Unified Engineering
            Report Architecture, Section 6), so it gets its own indigo-toned pair
            of cards rather than folding into the Verdict row above (Section 11).
          */}
          <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard
              label={`Reports Published (${overview?.windowDays ?? 30}d)`}
              value={overview?.totalReportsPublished ?? 0}
              tone="report"
            />
            <StatCard
              label="AI Included"
              value={overview?.reportsByAiStatus?.INCLUDED ?? 0}
              tone="report"
            />
          </div>

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
            <Badge tone="bg-violet-100 text-violet-700" uppercase>
              Advisory &middot; AI Generated
            </Badge>
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
  suffix,
}: {
  label: string
  value: number
  tone?: 'critical' | 'high' | 'ai' | 'ai-alert' | 'approved' | 'blocked' | 'report'
  suffix?: string
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
            : tone === 'approved'
              ? 'text-emerald-700'
              : tone === 'blocked' && value > 0
                ? 'text-red-700'
                : tone === 'report'
                  ? 'text-indigo-700'
                  : 'text-slate-900'

  // AI-toned cards get a violet border; verdict-toned cards get the same
  // emerald/red governance pairing as VerdictsPage/VerdictDetailPage; report-
  // toned cards get the same indigo used on AnalysisRunDetailPage's own
  // Engineering Report section - every engine's stat cards are bordered in
  // that engine's own accent color, so each section reads as visually
  // distinct even at a glance.
  const borderClassName =
    tone === 'ai' || tone === 'ai-alert'
      ? 'border-violet-200'
      : tone === 'approved'
        ? 'border-emerald-200'
        : tone === 'blocked'
          ? 'border-red-200'
          : tone === 'report'
            ? 'border-indigo-200'
            : 'border-slate-200'

  return (
    <div className={`rounded-lg border ${borderClassName} bg-white p-6 shadow-sm transition-shadow hover:shadow-md`}>
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${valueClassName}`}>
        {value}
        {suffix}
      </p>
    </div>
  )
}

function blockRatePercent(total: number | undefined, blocked: number | undefined): number {
  if (!total) {
    return 0
  }
  return Math.round(((blocked ?? 0) / total) * 100)
}
