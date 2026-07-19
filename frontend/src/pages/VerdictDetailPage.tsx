import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { VERDICT_OUTCOME_BANNER_TONES } from '../components/ui/badgeTones'
import { verdictService } from '../services/verdictService'
import type { VerdictDetail } from '../types/verdict'

export function VerdictDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [verdict, setVerdict] = useState<VerdictDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!id) {
      return
    }
    setIsLoading(true)
    verdictService
      .getById(Number(id))
      .then(setVerdict)
      .finally(() => setIsLoading(false))
  }, [id])

  if (isLoading) {
    return (
      <AppLayout>
        <LoadingSpinner />
      </AppLayout>
    )
  }

  if (!verdict) {
    return (
      <AppLayout>
        <p className="text-sm text-slate-500">Verdict not found.</p>
      </AppLayout>
    )
  }

  return (
    <AppLayout
      title={
        <>
          {verdict.repositoryFullName} #{verdict.pullRequestNumber}
        </>
      }
      breadcrumbs={[{ label: 'Verdicts', to: '/verdicts' }, { label: `#${verdict.pullRequestNumber}` }]}
    >
      <div className={`mb-6 rounded-lg border-2 p-6 shadow-sm ${VERDICT_OUTCOME_BANNER_TONES[verdict.outcome]}`}>
        <p className="text-xs font-semibold uppercase tracking-wide opacity-75">Governance Decision</p>
        <p className="mt-1 text-3xl font-bold">{verdict.outcome}</p>
        <p className="mt-2 text-sm opacity-80">
          Commit {verdict.commitSha.slice(0, 7)} &middot; Decided {new Date(verdict.createdAt).toLocaleString()}
        </p>
      </div>

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Reasons</h2>
      <Table>
        <TableHead>
          <tr>
            <th className="px-4 py-2">Rule</th>
            <th className="px-4 py-2">Blocking</th>
            <th className="px-4 py-2">Explanation</th>
          </tr>
        </TableHead>
        <TableBody>
          {verdict.reasons.length ? (
            verdict.reasons.map((reason) => (
              <tr key={reason.id}>
                <td className="px-4 py-2 text-slate-700">{reason.ruleId}</td>
                <td className="px-4 py-2">
                  <Badge tone={reason.blocking ? 'bg-red-100 text-red-800' : 'bg-slate-100 text-slate-600'}>
                    {reason.blocking ? 'Blocking' : 'Informational'}
                  </Badge>
                </td>
                <td className="px-4 py-2 text-slate-700">{reason.message}</td>
              </tr>
            ))
          ) : (
            <EmptyTableRow colSpan={3}>No reasons recorded - nothing was flagged.</EmptyTableRow>
          )}
        </TableBody>
      </Table>
    </AppLayout>
  )
}
