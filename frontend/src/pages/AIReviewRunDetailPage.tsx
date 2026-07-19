import { useEffect, useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
import { ErrorState } from '../components/ui/ErrorState'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { AI_CONFIDENCE_TONES, AI_REVIEW_RUN_STATUS_TONES } from '../components/ui/badgeTones'
import { aiReviewRunService } from '../services/aiReviewRunService'
import { aiReviewFindingService } from '../services/aiReviewFindingService'
import type { AIReviewRunDetail } from '../types/aiReviewRun'
import type { AIReviewConfidence, AIReviewFinding } from '../types/aiReviewFinding'

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
    <AppLayout
      title={
        <>
          {run.repositoryFullName} #{run.pullRequestNumber}
        </>
      }
      eyebrow={
        <Badge tone="bg-violet-100 text-violet-700" uppercase>
          Advisory &middot; AI Generated
        </Badge>
      }
      breadcrumbs={[{ label: 'AI Review Runs', to: '/ai-review-runs' }, { label: `#${run.pullRequestNumber}` }]}
    >
      <Card className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4" border="border border-violet-200">
        <Field
          label="Status"
          value={<Badge tone={AI_REVIEW_RUN_STATUS_TONES[run.status]}>{run.status}</Badge>}
        />
        <Field label="Provider" value={run.provider} />
        <Field label="Model" value={run.model} />
        <Field label="Prompt version" value={run.promptVersion} />
        <Field label="Commit" value={run.commitSha.slice(0, 7)} />
        <Field label="Created" value={new Date(run.createdAt).toLocaleString()} />
        <Field label="Updated" value={new Date(run.updatedAt).toLocaleString()} />
      </Card>

      {run.summary && (
        <div className="mb-6 rounded-lg border border-violet-200 bg-violet-50 p-4 text-sm text-violet-900">
          <p className="font-medium">Summary</p>
          <p className="mt-1">{run.summary}</p>
        </div>
      )}

      {run.failureReason && <ErrorState message={run.failureReason} />}

      <h2 className="mb-2 text-lg font-semibold text-slate-900">AI Findings</h2>
      <div className="mb-3 flex flex-wrap gap-2">
        {Object.entries(run.findingsByConfidence).map(([confidence, count]) => (
          <Badge key={confidence} tone={AI_CONFIDENCE_TONES[confidence as AIReviewConfidence] ?? 'bg-slate-100 text-slate-700'}>
            {confidence}: {count}
          </Badge>
        ))}
      </div>

      <Table tone="ai">
        <TableHead tone="ai">
          <tr>
            <th className="px-4 py-2">Confidence</th>
            <th className="px-4 py-2">Type</th>
            <th className="px-4 py-2">Location</th>
            <th className="px-4 py-2">Observation</th>
            <th className="px-4 py-2">Recommendation</th>
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
                <td className="px-4 py-2 text-slate-500">{finding.recommendation ?? '—'}</td>
              </tr>
            ))
          ) : (
            <EmptyTableRow colSpan={5}>No AI findings for this run.</EmptyTableRow>
          )}
        </TableBody>
      </Table>
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
