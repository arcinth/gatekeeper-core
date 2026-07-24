import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ExternalLink } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Surface, SectionHeading } from '../components/ui/Surface'
import { Chip } from '../components/ui/Chip'
import { Label, Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonTiles } from '../components/ui/Skeleton'
import { buttonClasses } from '../components/ui/buttonClasses'
import { ProportionBar, StatTile } from '../components/domain/StatTile'
import { humanize } from '../components/ui/tones'
import { percent, sumBuckets } from '../lib/format'
import { repositoryGovernanceService } from '../services/repositoryGovernanceService'
import { repositoryService } from '../services/repositoryService'
import type { Repository } from '../types/repository'
import type { RepositoryGovernance } from '../types/repositoryGovernance'

const WINDOW_OPTIONS = [7, 30, 90]

/**
 * One repository's governance posture. This replaces the standalone
 * "Repository Governance" destination (Product Experience spec, §05): a
 * repository's health was never a separate thing from the repository, and the
 * old dual `/repositories/governance` + `/repositories/:id/governance` routes
 * left it ambiguous whether it was a destination or a drill-down.
 */
export function RepositoryDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [repository, setRepository] = useState<Repository | null>(null)
  const [governance, setGovernance] = useState<RepositoryGovernance | null>(null)
  const [windowDays, setWindowDays] = useState(30)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!id) {
      return
    }
    setIsLoading(true)
    setError(null)
    try {
      // There is no single-repository read endpoint, so the list is filtered
      // client-side rather than adding a backend API for this one screen.
      const [repositories, governanceResult] = await Promise.all([
        repositoryService.list(),
        repositoryGovernanceService.getByRepositoryId(Number(id), windowDays),
      ])
      setRepository(repositories.find((candidate) => candidate.id === Number(id)) ?? null)
      setGovernance(governanceResult)
    } catch {
      setError('Failed to load this repository.')
    } finally {
      setIsLoading(false)
    }
  }, [id, windowDays])

  useEffect(() => {
    void load()
  }, [load])

  if (error) {
    return (
      <AppLayout width="narrow" breadcrumbs={[{ label: 'Repositories', to: '/repositories' }, { label: 'Detail' }]}>
        <ErrorState message={error} onRetry={() => void load()} />
      </AppLayout>
    )
  }

  const fullName = repository?.fullName ?? governance?.repositoryFullName ?? 'Repository'
  const blocked = governance?.verdictsByOutcome?.BLOCKED ?? 0
  const totalVerdicts = governance?.totalVerdicts ?? 0
  const criticalSecurity = governance?.securityFindingsBySeverity?.CRITICAL ?? 0
  const failedRuns = governance?.runsByStatus?.FAILED ?? 0

  return (
    <AppLayout
      eyebrow="Repository"
      title={fullName}
      description={repository?.description ?? 'Governance posture over the selected window.'}
      breadcrumbs={[{ label: 'Repositories', to: '/repositories' }, { label: fullName }]}
      actions={
        <>
          {repository && <Chip tone={repository.active ? 'pass' : 'neutral'}>{repository.active ? 'Active' : 'Inactive'}</Chip>}
          <a
            href={`https://github.com/${fullName}`}
            target="_blank"
            rel="noopener noreferrer"
            className={buttonClasses('secondary', 'md')}
          >
            View on GitHub
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </>
      }
    >
      <div className="mb-5 w-40">
        <Label htmlFor="window">Window</Label>
        <Select id="window" value={windowDays} onChange={(event) => setWindowDays(Number(event.target.value))}>
          {WINDOW_OPTIONS.map((days) => (
            <option key={days} value={days}>
              Last {days} days
            </option>
          ))}
        </Select>
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-5">
          <SkeletonTiles count={4} />
          <SkeletonTiles count={2} />
        </div>
      ) : !governance ? (
        <EmptyState title="No governance data" description="This repository has not been analyzed yet." />
      ) : (
        <div className="flex flex-col gap-6">
          <section>
            <SectionHeading eyebrow="Posture" title="Governance" />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile
                label="Block rate"
                value={percent(blocked, totalVerdicts)}
                suffix="%"
                tone={blocked > 0 ? 'block' : 'pass'}
                caption={`${blocked} of ${totalVerdicts} verdicts`}
              />
              <StatTile label="Analysis runs" value={governance.totalAnalysisRuns} caption={`Last ${windowDays} days`} />
              <StatTile
                label="Failed runs"
                value={failedRuns}
                tone={failedRuns > 0 ? 'block' : 'pass'}
                caption={failedRuns > 0 ? 'Pipeline needs attention' : 'All runs completed'}
              />
              <StatTile
                label="Critical security"
                value={criticalSecurity}
                tone={criticalSecurity > 0 ? 'block' : 'pass'}
                caption={criticalSecurity > 0 ? 'Needs triage now' : 'Nothing critical'}
              />
            </div>
          </section>

          <section>
            <SectionHeading eyebrow="Findings" title="What was found" />
            <div className="grid gap-3 lg:grid-cols-2">
              <Surface>
                <p className="mb-3 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Security severity</p>
                <ProportionBar
                  className="mb-4"
                  segments={[
                    { tone: 'block', value: criticalSecurity, label: 'Critical' },
                    { tone: 'warn', value: governance.securityFindingsBySeverity?.HIGH ?? 0, label: 'High' },
                    { tone: 'accent', value: governance.securityFindingsBySeverity?.MEDIUM ?? 0, label: 'Medium' },
                    { tone: 'neutral', value: governance.securityFindingsBySeverity?.LOW ?? 0, label: 'Low' },
                  ]}
                />
                <BucketList
                  buckets={governance.securityFindingsByCategory}
                  emptyLabel="No security findings in this window."
                />
              </Surface>

              <Surface>
                <p className="mb-3 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Policy severity</p>
                <ProportionBar
                  className="mb-4"
                  segments={[
                    { tone: 'block', value: governance.findingsBySeverity?.CRITICAL ?? 0, label: 'Critical' },
                    { tone: 'warn', value: governance.findingsBySeverity?.HIGH ?? 0, label: 'High' },
                    { tone: 'accent', value: governance.findingsBySeverity?.MEDIUM ?? 0, label: 'Medium' },
                    { tone: 'neutral', value: governance.findingsBySeverity?.LOW ?? 0, label: 'Low' },
                  ]}
                />
                <BucketList buckets={governance.findingsByCategory} emptyLabel="No policy findings in this window." />
              </Surface>
            </div>
          </section>

          <section>
            <SectionHeading eyebrow="Advisory" title="AI review" actions={<Chip tone="ai">Never affects verdicts</Chip>} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile label="Suggestions" value={governance.totalAiReviewFindings} tone="ai" />
              <StatTile
                label="High confidence"
                value={governance.aiReviewFindingsByConfidence?.HIGH ?? 0}
                tone="ai"
                caption="Still advisory"
              />
              <StatTile label="Reports published" value={governance.totalReportsPublished} tone="accent" />
              <StatTile
                label="AI included"
                value={governance.reportsByAiStatus?.INCLUDED ?? 0}
                tone="ai"
                caption="Reports that carried AI input"
              />
            </div>
          </section>
        </div>
      )}
    </AppLayout>
  )
}

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
