import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { verdictService } from '../services/verdictService'
import type { VerdictDetail, VerdictOutcome } from '../types/verdict'

// The same emerald/red governance pairing as VerdictsPage - see its comment
// for why this must not reuse the amber/orange severity scale or AI's violet.
const OUTCOME_BANNER_STYLES: Record<VerdictOutcome, string> = {
  APPROVED: 'border-emerald-300 bg-emerald-50 text-emerald-900',
  BLOCKED: 'border-red-300 bg-red-50 text-red-900',
}

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
    <AppLayout>
      <Link to="/verdicts" className="mb-4 inline-block text-sm text-slate-500 hover:underline">
        &larr; Back to Verdicts
      </Link>
      <h1 className="mb-4 text-xl font-semibold text-slate-900">
        {verdict.repositoryFullName} #{verdict.pullRequestNumber}
      </h1>

      <div className={`mb-6 rounded-lg border-2 p-6 shadow-sm ${OUTCOME_BANNER_STYLES[verdict.outcome]}`}>
        <p className="text-xs font-semibold uppercase tracking-wide opacity-75">Governance Decision</p>
        <p className="mt-1 text-3xl font-bold">{verdict.outcome}</p>
        <p className="mt-2 text-sm opacity-80">
          Commit {verdict.commitSha.slice(0, 7)} &middot; Decided {new Date(verdict.createdAt).toLocaleString()}
        </p>
      </div>

      <h2 className="mb-2 text-lg font-semibold text-slate-900">Reasons</h2>
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="min-w-full divide-y divide-slate-200 text-sm">
          <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-2">Rule</th>
              <th className="px-4 py-2">Blocking</th>
              <th className="px-4 py-2">Explanation</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {verdict.reasons.length ? (
              verdict.reasons.map((reason) => (
                <tr key={reason.id}>
                  <td className="px-4 py-2 text-slate-700">{reason.ruleId}</td>
                  <td className="px-4 py-2">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        reason.blocking ? 'bg-red-100 text-red-800' : 'bg-slate-100 text-slate-600'
                      }`}
                    >
                      {reason.blocking ? 'Blocking' : 'Informational'}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-slate-700">{reason.message}</td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-slate-500">
                  No reasons recorded - nothing was flagged.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </AppLayout>
  )
}
