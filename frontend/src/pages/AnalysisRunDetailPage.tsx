import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { analysisRunService } from '../services/analysisRunService'
import { policyFindingService } from '../services/policyFindingService'
import type { AnalysisRunDetail } from '../types/analysisRun'
import type { PolicyFinding } from '../types/policyFinding'

const SEVERITY_STYLES: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

export function AnalysisRunDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [run, setRun] = useState<AnalysisRunDetail | null>(null)
  const [findings, setFindings] = useState<PolicyFinding[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      return
    }
    setIsLoading(true)
    Promise.all([
      analysisRunService.getById(Number(id)),
      policyFindingService.list({ analysisRunId: Number(id), sort: 'severity,desc', size: 100 }),
    ])
      .then(([runDetail, findingsPage]) => {
        setRun(runDetail)
        setFindings(findingsPage.content)
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

      {run.failureReason && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-medium">Failure reason</p>
          <p className="mt-1">{run.failureReason}</p>
        </div>
      )}

      <div className="mb-6 flex gap-3">
        {Object.entries(run.findingsBySeverity).map(([severity, count]) => (
          <span
            key={severity}
            className={`rounded-full px-3 py-1 text-xs font-medium ${SEVERITY_STYLES[severity] ?? 'bg-slate-100 text-slate-700'}`}
          >
            {severity}: {count}
          </span>
        ))}
      </div>

      <h2 className="mb-3 text-lg font-semibold text-slate-900">Findings</h2>
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
                  No findings for this run.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </AppLayout>
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
