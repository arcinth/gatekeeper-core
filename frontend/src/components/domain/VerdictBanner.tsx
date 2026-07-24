import { CheckCircle2, CircleDashed, Clock, ShieldX, XCircle } from 'lucide-react'
import type { AnalysisRunStatus } from '../../types/analysisRun'
import type { VerdictOutcome } from '../../types/verdict'

/**
 * The single most important component in the product (Product Experience
 * spec, §10). Every pull request opens with it: the answer first, in the
 * largest type on the page, with one line of plain summary underneath.
 *
 * Deliberately calm when passing and weighted only when blocking - the spec's
 * "loud only when blocking" principle. A pass state that shouts is how
 * security tools train people to ignore them.
 */
export function VerdictBanner({
  outcome,
  runStatus,
  blockingCount,
  summary,
}: {
  outcome: VerdictOutcome | null
  runStatus: AnalysisRunStatus | null
  blockingCount: number
  summary?: string
}) {
  const state = resolveState(outcome, runStatus)

  return (
    <section
      className={`rounded-lg border p-6 ${state.container}`}
      aria-label={`Governance verdict: ${state.title}`}
    >
      <div className="flex items-start gap-4">
        <span className={`mt-0.5 shrink-0 ${state.iconColor}`} aria-hidden="true">
          {state.icon}
        </span>
        <div className="min-w-0">
          <p className="font-mono text-[10px] uppercase tracking-[0.14em] text-faint">Governance verdict</p>
          <h2 className={`mt-1.5 text-3xl font-semibold tracking-tight ${state.titleColor}`}>{state.title}</h2>
          <p className="mt-2 max-w-2xl text-sm text-muted">{summary ?? state.defaultSummary(blockingCount)}</p>
        </div>
      </div>
    </section>
  )
}

interface VerdictState {
  title: string
  container: string
  titleColor: string
  iconColor: string
  icon: React.ReactNode
  defaultSummary: (blockingCount: number) => string
}

function resolveState(outcome: VerdictOutcome | null, runStatus: AnalysisRunStatus | null): VerdictState {
  if (outcome === 'BLOCKED') {
    return {
      title: 'Merge blocked',
      container: 'border-block-line bg-block-bg',
      titleColor: 'text-content',
      iconColor: 'text-block',
      icon: <ShieldX className="h-7 w-7" />,
      defaultSummary: (count) =>
        count === 1
          ? 'One blocking finding must be resolved before this pull request can merge.'
          : `${count} blocking findings must be resolved before this pull request can merge.`,
    }
  }

  if (outcome === 'APPROVED') {
    return {
      title: 'Cleared to merge',
      container: 'border-line bg-surface',
      titleColor: 'text-content',
      iconColor: 'text-pass',
      icon: <CheckCircle2 className="h-7 w-7" />,
      defaultSummary: () => 'No blocking policy or security findings. GateKeeper raises no objection to this merge.',
    }
  }

  if (runStatus === 'FAILED') {
    return {
      title: 'Analysis failed',
      container: 'border-block-line bg-block-bg',
      titleColor: 'text-content',
      iconColor: 'text-block',
      icon: <XCircle className="h-7 w-7" />,
      defaultSummary: () => 'GateKeeper could not complete its analysis, so no governance decision was produced.',
    }
  }

  if (runStatus === 'IN_PROGRESS' || runStatus === 'QUEUED' || runStatus === 'RECEIVED') {
    return {
      title: 'Analysis in progress',
      container: 'border-warn-line bg-warn-bg',
      titleColor: 'text-content',
      iconColor: 'text-warn',
      icon: <Clock className="h-7 w-7" />,
      defaultSummary: () => 'GateKeeper is still checking this commit. A verdict appears here as soon as it completes.',
    }
  }

  return {
    title: 'No analysis yet',
    container: 'border-line bg-surface',
    titleColor: 'text-content',
    iconColor: 'text-faint',
    icon: <CircleDashed className="h-7 w-7" />,
    defaultSummary: () => 'No analysis has run against this pull request yet.',
  }
}
