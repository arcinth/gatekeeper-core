import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { aiReviewRunService } from '../services/aiReviewRunService'
import { aiReviewFindingService } from '../services/aiReviewFindingService'
import type { AIReviewRunDetail, AIReviewRunStatus } from '../types/aiReviewRun'
import type { AIReviewConfidence, AIReviewFinding } from '../types/aiReviewFinding'

// Deliberately violet throughout this page, distinct from every severity color used on the
// deterministic (Policy/Security) findings tables - AI Review results are advisory only and
// must read as visually distinct, not just differently labeled (Sprint 4 Milestone 4).
const CONFIDENCE_STYLES: Record<AIReviewConfidence, string> = {
  LOW: 'bg-violet-50 text-violet-600',
  MEDIUM: 'bg-violet-100 text-violet-700',
  HIGH: 'bg-violet-200 text-violet-900',
}

const STATUS_STYLES: Record<AIReviewRunStatus, string> = {
  COMPLETED: 'bg-violet-100 text-violet-800',
  FAILED: 'bg-red-100 text-red-800',
}

export function AIReviewRunDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [run, setRun] = useState<AIReviewRunDetail | null>(null)
  const [findings, setFindings] = useState<AIReviewFinding[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      return
    }
    setIsLoading(true)
    Promise.all([
      aiReviewRunService.getById(Number(id)),
      aiReviewFindingService.list({ aiReviewRunId: Number(id), sort: 'confidence,desc', size: 100 }),
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
        <p className="text-sm text-slate-500">AI review run not found.</p>
      </AppLayout>
    )
  }

  return (
    <AppLayout>
      <Link to="/ai-review-runs" className="mb-4 inline-block text-sm text-slate-500 hover:underline">
        &larr; Back to AI Review Runs
      </Link>
      <div className="mb-4 flex items-center gap-3">
        <h1 className="text-xl font-semibold text-slate-900">
          {run.repositoryFullName} #{run.pullRequestNumber}
        </h1>
        <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-violet-700">
          Advisory &middot; AI Generated
        </span>
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 rounded-lg border border-violet-200 bg-white p-6 shadow-sm md:grid-cols-4">
        <Field
          label="Status"
          value={<span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[run.status]}`}>{run.status}</span>}
        />
        <Field label="Provider" value={run.provider} />
        <Field label="Model" value={run.model} />
        <Field label="Prompt version" value={run.promptVersion} />
        <Field label="Commit" value={run.commitSha.slice(0, 7)} />
        <Field label="Created" value={new Date(run.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(run.updatedAt).toLocaleString()} />
      </div>

      {run.summary && (
        <div className="mb-6 rounded-lg border border-violet-200 bg-violet-50 p-4 text-sm text-violet-900">
          <p className="font-medium">Summary</p>
          <p className="mt-1">{run.summary}</p>
        </div>
      )}

      {run.failureReason && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-medium">Failure reason</p>
          <p className="mt-1">{run.failureReason}</p>
        </div>
      )}

      <h2 className="mb-2 text-lg font-semibold text-slate-900">AI Findings</h2>
      <div className="mb-3 flex gap-3">
        {Object.entries(run.findingsByConfidence).map(([confidence, count]) => (
          <span
            key={confidence}
            className={`rounded-full px-3 py-1 text-xs font-medium ${CONFIDENCE_STYLES[confidence as AIReviewConfidence] ?? 'bg-slate-100 text-slate-700'}`}
          >
            {confidence}: {count}
          </span>
        ))}
      </div>

      <div className="overflow-x-auto rounded-lg border border-violet-200 bg-white shadow-sm">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-violet-50 text-left text-xs font-medium uppercase tracking-wide text-violet-700">
            <tr>
              <th className="px-4 py-2">Confidence</th>
              <th className="px-4 py-2">Type</th>
              <th className="px-4 py-2">Location</th>
              <th className="px-4 py-2">Observation</th>
              <th className="px-4 py-2">Recommendation</th>
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
                  <td className="px-4 py-2 text-slate-500">{finding.recommendation ?? '—'}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                  No AI findings for this run.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </AppLayout>
  )
}

function Field({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
    </div>
  )
}
