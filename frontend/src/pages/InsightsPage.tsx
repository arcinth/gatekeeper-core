import { useCallback, useEffect, useState } from 'react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface, SectionHeading } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { Label, Select } from '../components/ui/Field'
import { ErrorState } from '../components/ui/states'
import { SkeletonTiles } from '../components/ui/Skeleton'
import { ProportionBar, StatTile } from '../components/domain/StatTile'
import { humanize } from '../components/ui/tones'
import { percent, sumBuckets } from '../lib/format'
import { dashboardService } from '../services/dashboardService'
import type { DashboardOverview } from '../types/dashboard'

const WINDOW_OPTIONS = [7, 30, 90]

/**
 * The executive overview (Product Experience spec, §06 Surface B).
 *
 * This is what the old Dashboard should have been. The daily "what needs me"
 * job moved to the Inbox, which frees this screen to answer the manager's
 * question instead: is the team healthy, and what is driving the blocks?
 *
 * Every tile here carries a comparison, a proportion or a caption - per the
 * spec's rule that a bare number is trivia and only a number with direction
 * or context earns its place.
 */
export function InsightsPage() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [windowDays, setWindowDays] = useState(30)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setOverview(await dashboardService.getOverview(windowDays))
    } catch {
      setError('Failed to load insights.')
    } finally {
      setIsLoading(false)
    }
  }, [windowDays])

  useEffect(() => {
    void load()
  }, [load])

  const blocked = overview?.verdictsByOutcome?.BLOCKED ?? 0
  const approved = overview?.verdictsByOutcome?.APPROVED ?? 0
  const totalVerdicts = overview?.totalVerdicts ?? 0
  const blockRate = percent(blocked, totalVerdicts)
  const failedRuns = overview?.runsByStatus?.FAILED ?? 0
  const totalRuns = overview?.totalAnalysisRuns ?? 0

  const criticalSecurity = overview?.securityFindingsBySeverity?.CRITICAL ?? 0
  const highSecurity = overview?.securityFindingsBySeverity?.HIGH ?? 0

  return (
    <AppLayout
      eyebrow="Governance"
      title="Insights"
      description={`How your organization's merge gate has behaved over the last ${windowDays} days.`}
      actions={
        <div className="w-40">
          <Label htmlFor="window">Window</Label>
          <Select id="window" value={windowDays} onChange={(event) => setWindowDays(Number(event.target.value))}>
            {WINDOW_OPTIONS.map((days) => (
              <option key={days} value={days}>
                Last {days} days
              </option>
            ))}
          </Select>
        </div>
      }
    >
      {error && <ErrorState message={error} onRetry={() => void load()} className="mb-5" />}

      {isLoading ? (
        <div className="flex flex-col gap-5">
          <SkeletonTiles count={4} />
          <SkeletonTiles count={2} />
        </div>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Governance posture - the headline the whole product exists to report. */}
          <section>
            <SectionHeading eyebrow="Posture" title="Governance" />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile
                label="Block rate"
                value={blockRate}
                suffix="%"
                tone={blockRate > 0 ? 'block' : 'pass'}
                caption={`${blocked} of ${totalVerdicts} verdicts blocked a merge`}
              />
              <StatTile
                label="Cleared to merge"
                value={approved}
                tone="pass"
                caption="Pull requests GateKeeper raised no objection to"
              />
              <StatTile
                label="Blocked"
                value={blocked}
                tone={blocked > 0 ? 'block' : 'neutral'}
                caption="Merges stopped before reaching production"
              />
              <StatTile
                label="Reports published"
                value={overview?.totalReportsPublished ?? 0}
                tone="accent"
                caption={`${overview?.reportsByAiStatus?.INCLUDED ?? 0} included AI advisory input`}
              />
            </div>
          </section>

          {/* What's driving blocks - the insight that lets a team fix the source. */}
          <section>
            <SectionHeading eyebrow="Drivers" title="What's driving blocks" />
            <div className="grid gap-3 lg:grid-cols-2">
              <Surface>
                <p className="mb-3 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">
                  Security findings by severity
                </p>
                <ProportionBar
                  className="mb-4"
                  segments={[
                    { tone: 'block', value: criticalSecurity, label: 'Critical' },
                    { tone: 'warn', value: highSecurity, label: 'High' },
                    { tone: 'accent', value: overview?.securityFindingsBySeverity?.MEDIUM ?? 0, label: 'Medium' },
                    { tone: 'neutral', value: overview?.securityFindingsBySeverity?.LOW ?? 0, label: 'Low' },
                  ]}
                />
                <BucketList buckets={overview?.securityFindingsByCategory} emptyLabel="No security findings recorded." />
              </Surface>

              <Surface>
                <p className="mb-3 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">
                  Policy findings by severity
                </p>
                <ProportionBar
                  className="mb-4"
                  segments={[
                    { tone: 'block', value: overview?.findingsBySeverity?.CRITICAL ?? 0, label: 'Critical' },
                    { tone: 'warn', value: overview?.findingsBySeverity?.HIGH ?? 0, label: 'High' },
                    { tone: 'accent', value: overview?.findingsBySeverity?.MEDIUM ?? 0, label: 'Medium' },
                    { tone: 'neutral', value: overview?.findingsBySeverity?.LOW ?? 0, label: 'Low' },
                  ]}
                />
                <BucketList buckets={overview?.findingsByCategory} emptyLabel="No policy findings recorded." />
              </Surface>
            </div>
          </section>

          {/* Pipeline health - is the gate itself working? */}
          <section>
            <SectionHeading eyebrow="Reliability" title="Pipeline health" />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile
                label="Repositories"
                value={overview?.totalRepositories ?? 0}
                caption="Connected and under governance"
              />
              <StatTile label="Analysis runs" value={totalRuns} caption={`Across the last ${windowDays} days`} />
              <StatTile
                label="Failed runs"
                value={failedRuns}
                tone={failedRuns > 0 ? 'block' : 'pass'}
                caption={
                  totalRuns > 0
                    ? `${percent(failedRuns, totalRuns)}% of runs did not complete`
                    : 'No runs in this window'
                }
              />
              <StatTile
                label="Critical security"
                value={criticalSecurity}
                tone={criticalSecurity > 0 ? 'block' : 'pass'}
                caption={criticalSecurity > 0 ? 'Needs triage now' : 'Nothing critical outstanding'}
              />
            </div>
          </section>

          {/* AI advisory, kept visually separate from every deterministic signal. */}
          <section>
            <SectionHeading
              eyebrow="Advisory"
              title="AI review"
              actions={<Chip tone="ai">Never affects verdicts</Chip>}
            />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile label="AI review runs" value={overview?.totalAiReviewRuns ?? 0} tone="ai" />
              <StatTile
                label="Failed reviews"
                value={overview?.aiReviewRunsByStatus?.FAILED ?? 0}
                tone={(overview?.aiReviewRunsByStatus?.FAILED ?? 0) > 0 ? 'warn' : 'neutral'}
                caption="A failed AI review never blocks a merge"
              />
              <StatTile label="Suggestions raised" value={overview?.totalAiReviewFindings ?? 0} tone="ai" />
              <StatTile
                label="High confidence"
                value={overview?.aiReviewFindingsByConfidence?.HIGH ?? 0}
                tone="ai"
                caption="Still advisory, regardless of confidence"
              />
            </div>
          </section>
        </div>
      )}
    </AppLayout>
  )
}

/** Renders a Partial<Record<Enum, number>> bucket map as a ranked list. */
function BucketList({
  buckets,
  emptyLabel,
}: {
  buckets: Partial<Record<string, number>> | undefined
  emptyLabel: string
}) {
  const total = sumBuckets(buckets)
  if (!buckets || total === 0) {
    return <p className="text-sm text-muted">{emptyLabel}</p>
  }

  const entries = Object.entries(buckets)
    .filter((entry): entry is [string, number] => (entry[1] ?? 0) > 0)
    .sort((a, b) => b[1] - a[1])

  return (
    <ul className="flex flex-col gap-2">
      {entries.map(([key, value]) => (
        <li key={key} className="flex items-center justify-between gap-3 text-sm">
          <span className="truncate text-muted">{humanize(key)}</span>
          <span className="tabular shrink-0 font-mono text-xs text-content">
            {value}
            <span className="ml-1.5 text-faint">{percent(value, total)}%</span>
          </span>
        </li>
      ))}
    </ul>
  )
}
