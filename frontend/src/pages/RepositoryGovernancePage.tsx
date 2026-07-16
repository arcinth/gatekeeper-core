import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { repositoryService } from '../services/repositoryService'
import { repositoryGovernanceService } from '../services/repositoryGovernanceService'
import { analysisRunService } from '../services/analysisRunService'
import { verdictService } from '../services/verdictService'
import type { Repository } from '../types/repository'
import type { RepositoryGovernance } from '../types/repositoryGovernance'
import type { AnalysisRunStatus, AnalysisRunSummary } from '../types/analysisRun'
import type { VerdictOutcome, VerdictSummary } from '../types/verdict'

const RECENT_SIZE = 5

const STATUS_STYLES: Record<AnalysisRunStatus, string> = {
  RECEIVED: 'bg-slate-100 text-slate-700',
  QUEUED: 'bg-slate-100 text-slate-700',
  IN_PROGRESS: 'bg-amber-100 text-amber-800',
  COMPLETED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
}

// Same emerald/red governance pairing used everywhere else a Verdict outcome
// is shown (VerdictsPage, AnalysisRunsPage, AnalysisRunDetailPage) - the
// established colors, not a page-local variant.
const OUTCOME_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'bg-emerald-100 text-emerald-800',
  BLOCKED: 'bg-red-100 text-red-800',
}

export function RepositoryGovernancePage() {
  const { id } = useParams<{ id?: string }>()
  const navigate = useNavigate()
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [governance, setGovernance] = useState<RepositoryGovernance | null>(null)
  const [recentRuns, setRecentRuns] = useState<AnalysisRunSummary[]>([])
  const [recentVerdicts, setRecentVerdicts] = useState<VerdictSummary[]>([])
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    if (!id) {
      setGovernance(null)
      setRecentRuns([])
      setRecentVerdicts([])
      return
    }
    setIsLoading(true)
    Promise.all([
      repositoryGovernanceService.getByRepositoryId(Number(id)),
      analysisRunService.list({ repositoryId: Number(id), size: RECENT_SIZE }),
      verdictService.list({ repositoryId: Number(id), size: RECENT_SIZE }),
    ])
      .then(([summary, runsPage, verdictsPage]) => {
        setGovernance(summary)
        setRecentRuns(runsPage.content)
        setRecentVerdicts(verdictsPage.content)
      })
      .finally(() => setIsLoading(false))
  }, [id])

  return (
    <AppLayout>
      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-semibold text-slate-900">Repository Governance</h1>
        <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-white">
          Per-Repository
        </span>
      </div>

      <div className="mb-6 flex items-center gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={id ?? ''}
          onChange={(event) => {
            if (event.target.value) {
              navigate(`/repositories/${event.target.value}/governance`)
            }
          }}
        >
          <option value="">{repositories.length ? 'Select a repository...' : 'No repositories connected'}</option>
          {repositories.map((repository) => (
            <option key={repository.id} value={repository.id}>
              {repository.fullName}
            </option>
          ))}
        </select>
      </div>

      {!id && <p className="text-sm text-slate-500">Select a repository to view its governance activity.</p>}

      {id && isLoading && <LoadingSpinner />}

      {id && !isLoading && governance && (
        <>
          <HealthSummary governance={governance} />

          <div className="mb-3 flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900">Governance</h2>
            <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-white">
              Verdicts
            </span>
          </div>
          <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label={`Verdicts (${governance.windowDays}d)`} value={governance.totalVerdicts} />
            <StatCard label="Approved" value={governance.verdictsByOutcome.APPROVED ?? 0} tone="approved" />
            <StatCard label="Blocked" value={governance.verdictsByOutcome.BLOCKED ?? 0} tone="blocked" />
            <StatCard
              label="Block Rate"
              value={blockRatePercent(governance.totalVerdicts, governance.verdictsByOutcome.BLOCKED)}
              suffix="%"
              tone={
                blockRatePercent(governance.totalVerdicts, governance.verdictsByOutcome.BLOCKED) > 0
                  ? 'blocked'
                  : undefined
              }
            />
          </div>

          <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard
              label={`Reports Published (${governance.windowDays}d)`}
              value={governance.totalReportsPublished}
              tone="report"
            />
            <StatCard label="AI Included" value={governance.reportsByAiStatus.INCLUDED ?? 0} tone="report" />
          </div>

          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label={`Analysis Runs (${governance.windowDays}d)`} value={governance.totalAnalysisRuns} />
            <StatCard label={`Policy Findings (${governance.windowDays}d)`} value={governance.totalFindings} />
            <StatCard label="Failed Runs" value={governance.runsByStatus.FAILED ?? 0} />
          </div>

          <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Security</h2>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label={`Security Findings (${governance.windowDays}d)`} value={governance.totalSecurityFindings} />
            <StatCard label="Critical" value={governance.securityFindingsBySeverity.CRITICAL ?? 0} tone="critical" />
            <StatCard label="High" value={governance.securityFindingsBySeverity.HIGH ?? 0} tone="high" />
            <StatCard label="Secrets Exposure" value={governance.securityFindingsByCategory.SECRETS_EXPOSURE ?? 0} />
          </div>

          {/*
            AI Review stays visually advisory here too: violet, its own
            established color everywhere else in the app, and no card here
            ever contributes to the Governance/Block Rate cards above it.
          */}
          <div className="mb-3 mt-8 flex items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900">AI Review</h2>
            <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-violet-700">
              Advisory &middot; AI Generated
            </span>
          </div>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label={`AI Findings (${governance.windowDays}d)`} value={governance.totalAiReviewFindings} tone="ai" />
            <StatCard label="High Confidence" value={governance.aiReviewFindingsByConfidence.HIGH ?? 0} tone="ai" />
          </div>

          <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Recent Analysis Runs</h2>
          <RecentRunsTable runs={recentRuns} />

          <h2 className="mb-3 mt-8 text-lg font-semibold text-slate-900">Recent Verdicts</h2>
          <RecentVerdictsTable verdicts={recentVerdicts} />
        </>
      )}
    </AppLayout>
  )
}

function HealthSummary({ governance }: { governance: RepositoryGovernance }) {
  const rate = blockRatePercent(governance.totalVerdicts, governance.verdictsByOutcome.BLOCKED)
  const health = rate === 0 ? 'HEALTHY' : rate <= 25 ? 'NEEDS_ATTENTION' : 'AT_RISK'

  const bannerStyles: Record<string, string> = {
    HEALTHY: 'border-emerald-300 bg-emerald-50 text-emerald-900',
    NEEDS_ATTENTION: 'border-amber-300 bg-amber-50 text-amber-900',
    AT_RISK: 'border-red-300 bg-red-50 text-red-900',
  }
  const labels: Record<string, string> = {
    HEALTHY: 'Healthy',
    NEEDS_ATTENTION: 'Needs Attention',
    AT_RISK: 'At Risk',
  }

  return (
    <div className={`mb-6 rounded-lg border-2 p-6 shadow-sm ${bannerStyles[health]}`}>
      <p className="text-xs font-semibold uppercase tracking-wide opacity-75">Repository Health</p>
      <div className="mt-1 flex flex-wrap items-baseline gap-3">
        <p className="text-2xl font-bold">{governance.repositoryFullName}</p>
        <span className="rounded-full bg-white/60 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide">
          {labels[health]}
        </span>
      </div>
      <p className="mt-2 text-sm opacity-80">
        {rate}% block rate over the last {governance.windowDays} days ({governance.verdictsByOutcome.BLOCKED ?? 0} of{' '}
        {governance.totalVerdicts} verdicts blocked).
      </p>
    </div>
  )
}

function RecentRunsTable({ runs }: { runs: AnalysisRunSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-2">Pull Request</th>
            <th className="px-4 py-2">Status</th>
            <th className="px-4 py-2">Verdict</th>
            <th className="px-4 py-2">Created</th>
            <th className="px-4 py-2">Report</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {runs.length ? (
            runs.map((run) => (
              <tr key={run.id} className="hover:bg-slate-50">
                <td className="px-4 py-2">
                  <Link to={`/analysis-runs/${run.id}`} className="text-slate-900 hover:underline">
                    #{run.pullRequestNumber} {run.pullRequestTitle}
                  </Link>
                </td>
                <td className="px-4 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[run.status]}`}>
                    {run.status}
                  </span>
                </td>
                <td className="px-4 py-2">
                  {run.verdictOutcome ? (
                    <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${OUTCOME_STYLES[run.verdictOutcome]}`}>
                      {run.verdictOutcome}
                    </span>
                  ) : (
                    <span className="text-slate-400">&mdash;</span>
                  )}
                </td>
                <td className="px-4 py-2 text-slate-500">{new Date(run.createdAt).toLocaleString()}</td>
                <td className="px-4 py-2">
                  <Link to={`/analysis-runs/${run.id}`} className="text-xs font-medium text-indigo-700 hover:underline">
                    View Report &rarr;
                  </Link>
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                No analysis runs for this repository yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function RecentVerdictsTable({ verdicts }: { verdicts: VerdictSummary[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-2">Pull Request</th>
            <th className="px-4 py-2">Outcome</th>
            <th className="px-4 py-2">Reasons</th>
            <th className="px-4 py-2">Decided</th>
            <th className="px-4 py-2">Report</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {verdicts.length ? (
            verdicts.map((verdict) => (
              <tr key={verdict.id} className="hover:bg-slate-50">
                <td className="px-4 py-2">
                  <Link to={`/verdicts/${verdict.id}`} className="text-slate-900 hover:underline">
                    #{verdict.pullRequestNumber}
                  </Link>
                </td>
                <td className="px-4 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-bold ${OUTCOME_STYLES[verdict.outcome]}`}>
                    {verdict.outcome}
                  </span>
                </td>
                <td className="px-4 py-2 text-slate-700">{verdict.reasonsTotal}</td>
                <td className="px-4 py-2 text-slate-500">{new Date(verdict.createdAt).toLocaleString()}</td>
                <td className="px-4 py-2">
                  <Link
                    to={`/analysis-runs/${verdict.analysisRunId}`}
                    className="text-xs font-medium text-indigo-700 hover:underline"
                  >
                    View Report &rarr;
                  </Link>
                </td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                No verdicts for this repository yet.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

// Verbatim copy of DashboardPage's own StatCard/blockRatePercent - this
// codebase's established convention is page-local render helpers (see
// AnalysisRunDetailPage's own Field/FindingsTable, AIReviewRunDetailPage's
// own Field), not a shared component extracted across pages, so DashboardPage
// itself stays untouched.
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
    <div className={`rounded-lg border ${borderClassName} bg-white p-6 shadow-sm`}>
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${valueClassName}`}>
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
