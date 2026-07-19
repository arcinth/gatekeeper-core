import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { analysisRunService } from '../services/analysisRunService'
import { policyFindingService } from '../services/policyFindingService'
import { securityFindingService } from '../services/securityFindingService'
import { reportService } from '../services/reportService'
import type { AnalysisRunDetail } from '../types/analysisRun'
import type { PolicyFinding } from '../types/policyFinding'
import type { SecurityFinding } from '../types/securityFinding'
import type { VerdictOutcome } from '../types/verdict'
import type { AIReviewConfidence, AIReviewFinding } from '../types/aiReviewFinding'
import type { AiReviewStatus, AuditLogEntry, ReportDetail } from '../types/report'

const SEVERITY_STYLES: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

// Same emerald/red governance pairing as VerdictDetailPage/VerdictsPage - the
// verdict banner here must read as visually distinct from the severity-badge
// findings tables below it, not as one more finding among many (Sprint 5
// Milestone 3).
const VERDICT_BANNER_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'border-emerald-300 bg-emerald-50 text-emerald-900',
  BLOCKED: 'border-red-300 bg-red-50 text-red-900',
}

// Indigo throughout the Engineering Report section - distinct from severity's
// amber/orange/red, governance's emerald/red, and AI's own violet: this
// section is the "meta" layer presenting all of them together, not another
// finding severity or a fifth engine (Unified Engineering Report
// Architecture, Section 10).
const AI_REVIEW_STATUS_STYLES: Record<AiReviewStatus, string> = {
  INCLUDED: 'bg-indigo-100 text-indigo-800',
  UNAVAILABLE: 'bg-amber-100 text-amber-800',
  DISABLED: 'bg-slate-100 text-slate-600',
}

// AI finding confidence keeps its own already-established violet language
// (mirrors AIReviewRunDetailPage) even inside the indigo-framed report
// section - confidence is AI-specific content, not report chrome.
const CONFIDENCE_STYLES: Record<AIReviewConfidence, string> = {
  LOW: 'bg-violet-50 text-violet-600',
  MEDIUM: 'bg-violet-100 text-violet-700',
  HIGH: 'bg-violet-200 text-violet-900',
}

export function AnalysisRunDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [run, setRun] = useState<AnalysisRunDetail | null>(null)
  const [findings, setFindings] = useState<PolicyFinding[]>([])
  const [securityFindings, setSecurityFindings] = useState<SecurityFinding[]>([])
  const [report, setReport] = useState<ReportDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      return
    }
    setIsLoading(true)
    Promise.all([
      analysisRunService.getById(Number(id)),
      policyFindingService.list({ analysisRunId: Number(id), sort: 'severity,desc', size: 100 }),
      securityFindingService.list({ analysisRunId: Number(id), sort: 'severity,desc', size: 100 }),
      reportService.getByAnalysisRunId(Number(id)),
    ])
      .then(([runDetail, findingsPage, securityFindingsPage, reportDetail]) => {
        setRun(runDetail)
        setFindings(findingsPage.content)
        setSecurityFindings(securityFindingsPage.content)
        setReport(reportDetail)
      })
      .finally(() => setIsLoading(false))
  }, [id])

  if (isLoading) {
    return (
      <AppLayout>
        <LoadingSpinner />
      </AppLayout>
    )
  }

  if (!run) {
    return (
      <AppLayout>
        <p className="text-sm text-slate-500">Analysis run not found.</p>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <Link to="/analysis-runs" className="mb-4 inline-block text-sm text-slate-500 hover:underline">
        &larr; Back to Analysis Runs
      </Link>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">
        {run.repository.fullName} #{run.pullRequest.number}
      </h1>
      <Link
        to={`/pull-requests/${run.pullRequestId}`}
        className="mb-4 inline-block text-sm text-slate-500 hover:underline"
      >
        View Pull Request &rarr;
      </Link>

      <div className="mb-6 grid grid-cols-2 gap-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm md:grid-cols-4">
        <Field label="Status" value={run.status} />
        <Field label="Trigger" value={run.triggerReason} />
        <Field label="Commit" value={run.commitSha.slice(0, 7)} />
        <Field label="Author" value={run.pullRequest.authorLogin} />
        <Field label="Source branch" value={run.pullRequest.sourceBranch} />
        <Field label="Target branch" value={run.pullRequest.targetBranch} />
        <Field label="Created" value={new Date(run.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(run.updatedAt).toLocaleString()} />
      </div>

      {run.verdictOutcome && (
        <div className={`mb-6 rounded-lg border-2 p-6 shadow-sm ${VERDICT_BANNER_STYLES[run.verdictOutcome]}`}>
          <p className="text-xs font-semibold uppercase tracking-wide opacity-75">Governance Decision</p>
          <p className="mt-1 text-3xl font-bold">{run.verdictOutcome}</p>
          {run.verdictReasons.length > 0 && (
            <ul className="mt-3 space-y-1 text-sm opacity-90">
              {run.verdictReasons.map((reason) => (
                <li key={reason.id}>
                  <span className="font-semibold">{reason.blocking ? 'Blocking' : 'Informational'}:</span>{' '}
                  {reason.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {report && (
        <div className="mb-6 rounded-lg border-2 border-indigo-200 bg-white p-6 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">Engineering Report</h2>
              <span className="rounded-full bg-indigo-800 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-white">
                Unified
              </span>
            </div>
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <span>Published {new Date(report.publishedAt).toLocaleString()}</span>
              <span
                className={`rounded-full px-2 py-0.5 text-xs font-medium ${AI_REVIEW_STATUS_STYLES[report.aiReviewStatus]}`}
              >
                AI {report.aiReviewStatus}
              </span>
            </div>
          </div>

          <h3 className="mb-2 text-sm font-semibold text-slate-700">AI Findings</h3>
          {report.aiReviewStatus === 'INCLUDED' ? (
            <AiFindingsTable findings={report.aiFindings} />
          ) : (
            <p className="mb-2 text-sm text-slate-500">
              {report.aiReviewStatus === 'DISABLED'
                ? 'AI review is disabled for this deployment.'
                : 'AI review did not complete in time, or failed, for this run.'}
            </p>
          )}

          <h3 className="mb-2 mt-6 text-sm font-semibold text-slate-700">Audit Timeline</h3>
          <AuditTimeline entries={report.auditTrail} />
        </div>
      )}

      {run.failureReason && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-medium">Failure reason</p>
          <p className="mt-1">{run.failureReason}</p>
        </div>
      )}

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Policy Findings</h2>
      <div className="mb-3 flex gap-3">
        {Object.entries(run.findingsBySeverity).map(([severity, count]) => (
          <span
            key={severity}
            className={`rounded-full px-3 py-1 text-xs font-medium ${SEVERITY_STYLES[severity] ?? 'bg-slate-100 text-slate-700'}`}
          >
            {severity}: {count}
          </span>
        ))}
      </div>
      <FindingsTable findings={findings} emptyMessage="No policy findings for this run." />

      <h2 className="mb-2 mt-8 text-lg font-semibold text-slate-900">Security Findings</h2>
      <div className="mb-3 flex gap-3">
        {Object.entries(run.securityFindingsBySeverity).map(([severity, count]) => (
          <span
            key={severity}
            className={`rounded-full px-3 py-1 text-xs font-medium ${SEVERITY_STYLES[severity] ?? 'bg-slate-100 text-slate-700'}`}
          >
            {severity}: {count}
          </span>
        ))}
      </div>
      <FindingsTable findings={securityFindings} emptyMessage="No security findings for this run." />
    </AppLayout>
  )
}

function FindingsTable({
  findings,
  emptyMessage,
}: {
  findings: (PolicyFinding | SecurityFinding)[]
  emptyMessage: string
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-2">Severity</th>
            <th className="px-4 py-2">Rule</th>
            <th className="px-4 py-2">Location</th>
            <th className="px-4 py-2">Message</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {findings.length ? (
            findings.map((finding) => (
              <tr key={finding.id}>
                <td className="px-4 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_STYLES[finding.severity]}`}>
                    {finding.severity}
                  </span>
                </td>
                <td className="px-4 py-2 text-slate-700">{finding.ruleId}</td>
                <td className="px-4 py-2 text-slate-500">
                  {finding.filePath}:{finding.lineNumber}
                </td>
                <td className="px-4 py-2 text-slate-700">{finding.message}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={4} className="px-4 py-6 text-center text-slate-500">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function AiFindingsTable({ findings }: { findings: AIReviewFinding[] }) {
  return (
    <div className="mb-2 overflow-x-auto rounded-lg border border-indigo-100 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-violet-50 text-left text-xs font-medium uppercase tracking-wide text-violet-700">
          <tr>
            <th className="px-4 py-2">Confidence</th>
            <th className="px-4 py-2">Type</th>
            <th className="px-4 py-2">Location</th>
            <th className="px-4 py-2">Observation</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {findings.length ? (
            findings.map((finding) => (
              <tr key={finding.id}>
                <td className="px-4 py-2">
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${CONFIDENCE_STYLES[finding.confidence]}`}>
                    {finding.confidence}
                  </span>
                </td>
                <td className="px-4 py-2 text-slate-700">{finding.type}</td>
                <td className="px-4 py-2 text-slate-500">
                  {finding.filePath}
                  {finding.lineNumber !== null ? `:${finding.lineNumber}` : ''}
                </td>
                <td className="px-4 py-2 text-slate-700">{finding.message}</td>
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={4} className="px-4 py-6 text-center text-slate-500">
                No AI findings for this run.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function AuditTimeline({ entries }: { entries: AuditLogEntry[] }) {
  if (!entries.length) {
    return <p className="text-sm text-slate-500">No audit events recorded.</p>
  }
  return (
    <ol className="space-y-3 border-l-2 border-indigo-200 pl-4">
      {entries.map((entry) => (
        <li key={entry.id} className="relative">
          <span className="absolute -left-5.25 top-1 h-2.5 w-2.5 rounded-full bg-indigo-400" />
          <p className="text-sm text-slate-700">{entry.summary}</p>
          <p className="text-xs text-slate-400">{new Date(entry.occurredAt).toLocaleString()}</p>
        </li>
      ))}
    </ol>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
    </div>
  )
}
