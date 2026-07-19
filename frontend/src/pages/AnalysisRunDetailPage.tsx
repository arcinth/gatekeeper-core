import { useEffect, useState } from 'react'
import { ArrowRight } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { buttonClasses } from '../components/ui/buttonClasses'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { AI_REVIEW_REPORT_STATUS_TONES, AI_CONFIDENCE_TONES, SEVERITY_TONES, VERDICT_OUTCOME_BANNER_TONES } from '../components/ui/badgeTones'
import { analysisRunService } from '../services/analysisRunService'
import { policyFindingService } from '../services/policyFindingService'
import { securityFindingService } from '../services/securityFindingService'
import { reportService } from '../services/reportService'
import type { AnalysisRunDetail } from '../types/analysisRun'
import type { PolicyFinding } from '../types/policyFinding'
import type { SecurityFinding } from '../types/securityFinding'
import type { AIReviewFinding } from '../types/aiReviewFinding'
import type { AuditLogEntry, ReportDetail } from '../types/report'

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
    <AppLayout
      title={
        <>
          {run.repository.fullName} #{run.pullRequest.number}
        </>
      }
      breadcrumbs={[
        { label: 'Analysis Runs', to: '/analysis-runs' },
        { label: `#${run.pullRequest.number} ${run.commitSha.slice(0, 7)}` },
      ]}
      actions={
        <Link to={`/pull-requests/${run.pullRequestId}`} className={buttonClasses('secondary', 'md')}>
          View Pull Request <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>
      }
    >
      <Card className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <Field label="Status" value={run.status} />
        <Field label="Trigger" value={run.triggerReason} />
        <Field label="Commit" value={run.commitSha.slice(0, 7)} />
        <Field label="Author" value={run.pullRequest.authorLogin} />
        <Field label="Source branch" value={run.pullRequest.sourceBranch} />
        <Field label="Target branch" value={run.pullRequest.targetBranch} />
        <Field label="Created" value={new Date(run.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(run.updatedAt).toLocaleString()} />
      </Card>

      {run.verdictOutcome && (
        <div className={`mb-6 rounded-lg border-2 p-6 shadow-sm ${VERDICT_OUTCOME_BANNER_TONES[run.verdictOutcome]}`}>
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
        <Card className="mb-6" border="border-2 border-indigo-200">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">Engineering Report</h2>
              <Badge tone="bg-indigo-800 text-white" uppercase>
                Unified
              </Badge>
            </div>
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <span>Published {new Date(report.publishedAt).toLocaleString()}</span>
              <Badge tone={AI_REVIEW_REPORT_STATUS_TONES[report.aiReviewStatus]}>AI {report.aiReviewStatus}</Badge>
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
        </Card>
      )}

      {run.failureReason && <ErrorState message={run.failureReason} />}

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Policy Findings</h2>
      <div className="mb-3 flex flex-wrap gap-2">
        {Object.entries(run.findingsBySeverity).map(([severity, count]) => (
          <Badge key={severity} tone={SEVERITY_TONES[severity as keyof typeof SEVERITY_TONES] ?? 'bg-slate-100 text-slate-700'}>
            {severity}: {count}
          </Badge>
        ))}
      </div>
      <FindingsTable findings={findings} emptyMessage="No policy findings for this run." />

      <h2 className="mb-2 mt-8 text-lg font-semibold text-slate-900">Security Findings</h2>
      <div className="mb-3 flex flex-wrap gap-2">
        {Object.entries(run.securityFindingsBySeverity).map(([severity, count]) => (
          <Badge key={severity} tone={SEVERITY_TONES[severity as keyof typeof SEVERITY_TONES] ?? 'bg-slate-100 text-slate-700'}>
            {severity}: {count}
          </Badge>
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
    <Table>
      <TableHead>
        <tr>
          <th className="px-4 py-2">Severity</th>
          <th className="px-4 py-2">Rule</th>
          <th className="px-4 py-2">Location</th>
          <th className="px-4 py-2">Message</th>
        </tr>
      </TableHead>
      <TableBody>
        {findings.length ? (
          findings.map((finding) => (
            <tr key={finding.id}>
              <td className="px-4 py-2">
                <Badge tone={SEVERITY_TONES[finding.severity]}>{finding.severity}</Badge>
              </td>
              <td className="px-4 py-2 text-slate-700">{finding.ruleId}</td>
              <td className="px-4 py-2 text-slate-500">
                {finding.filePath}:{finding.lineNumber}
              </td>
              <td className="px-4 py-2 text-slate-700">{finding.message}</td>
            </tr>
          ))
        ) : (
          <EmptyTableRow colSpan={4}>{emptyMessage}</EmptyTableRow>
        )}
      </TableBody>
    </Table>
  )
}

function AiFindingsTable({ findings }: { findings: AIReviewFinding[] }) {
  return (
    <div className="mb-2">
      <Table tone="ai">
        <TableHead tone="ai">
          <tr>
            <th className="px-4 py-2">Confidence</th>
            <th className="px-4 py-2">Type</th>
            <th className="px-4 py-2">Location</th>
            <th className="px-4 py-2">Observation</th>
          </tr>
        </TableHead>
        <TableBody>
          {findings.length ? (
            findings.map((finding) => (
              <tr key={finding.id}>
                <td className="px-4 py-2">
                  <Badge tone={AI_CONFIDENCE_TONES[finding.confidence]}>{finding.confidence}</Badge>
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
            <EmptyTableRow colSpan={4}>No AI findings for this run.</EmptyTableRow>
          )}
        </TableBody>
      </Table>
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
          <span className="absolute left-[-1.31rem] top-1 h-2.5 w-2.5 rounded-full bg-indigo-400" />
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
