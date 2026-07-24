import { useCallback, useEffect, useState } from 'react'
import { ShieldCheck } from 'lucide-react'
import { AppLayout } from '../layouts/AppLayout'
import { Label, Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { Chip } from '../components/ui/Chip'
import { FindingCard } from '../components/domain/FindingCard'
import { humanize, pullRequestStatusTone, severityTone } from '../components/ui/tones'
import { formatRelativeTime, githubLineUrl } from '../lib/format'
import { securityFindingService } from '../services/securityFindingService'
import { repositoryService } from '../services/repositoryService'
import type { PageResponse } from '../types/api'
import type { Repository } from '../types/repository'
import type { SecurityCategory, SecurityFinding, SecuritySeverity } from '../types/securityFinding'

const SEVERITIES: SecuritySeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const CATEGORIES: SecurityCategory[] = ['SECRETS_EXPOSURE', 'INSECURE_CRYPTOGRAPHY']

/**
 * The one findings list that survives as a destination (Product Experience
 * spec, §05) - but reframed from a flat dump of every finding ever produced
 * into a triage queue: what still needs attention, worst first, each one click
 * from the pull request that produced it.
 *
 * Defaults to CRITICAL because that is the security engineer's actual entry
 * point; everything else is one dropdown away.
 */
export function SecurityTriagePage() {
  const [page, setPage] = useState<PageResponse<SecurityFinding> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [severity, setSeverity] = useState<SecuritySeverity | ''>('CRITICAL')
  const [category, setCategory] = useState<SecurityCategory | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [showHistorical, setShowHistorical] = useState(false)
  const [pageNumber, setPageNumber] = useState(0)

  useEffect(() => {
    repositoryService
      .list()
      .then(setRepositories)
      .catch(() => {
        // Filter-only dependency; the queue still works without it.
      })
  }, [])

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setPage(
        await securityFindingService.list({
          page: pageNumber,
          size: 20,
          sort: 'createdAt,desc',
          severity: severity === '' ? undefined : severity,
          category: category === '' ? undefined : category,
          repositoryId: repositoryId === '' ? undefined : repositoryId,
          currentOnly: !showHistorical,
        }),
      )
    } catch {
      setError('Failed to load security findings.')
    } finally {
      setIsLoading(false)
    }
  }, [pageNumber, severity, category, repositoryId, showHistorical])

  useEffect(() => {
    void load()
  }, [load])

  const findings = page?.content ?? []

  return (
    <AppLayout
      eyebrow="Triage"
      title="Security"
      description={
        showHistorical
          ? 'Every recorded finding, including resolved and superseded ones.'
          : 'What still needs attention: open pull requests, latest commit only.'
      }
    >
      <div className="mb-5 flex flex-wrap items-end gap-3">
        <div className="w-full sm:w-44">
          <Label htmlFor="severity">Severity</Label>
          <Select
            id="severity"
            value={severity}
            onChange={(event) => {
              setSeverity(event.target.value as SecuritySeverity | '')
              setPageNumber(0)
            }}
          >
            <option value="">Any severity</option>
            {SEVERITIES.map((value) => (
              <option key={value} value={value}>
                {humanize(value)}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-full sm:w-56">
          <Label htmlFor="category">Category</Label>
          <Select
            id="category"
            value={category}
            onChange={(event) => {
              setCategory(event.target.value as SecurityCategory | '')
              setPageNumber(0)
            }}
          >
            <option value="">Any category</option>
            {CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {humanize(value)}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-full sm:w-64">
          <Label htmlFor="repository">Repository</Label>
          <Select
            id="repository"
            value={repositoryId}
            onChange={(event) => {
              setRepositoryId(event.target.value === '' ? '' : Number(event.target.value))
              setPageNumber(0)
            }}
          >
            <option value="">All repositories</option>
            {repositories.map((repository) => (
              <option key={repository.id} value={repository.id}>
                {repository.fullName}
              </option>
            ))}
          </Select>
        </div>
        <label className="flex h-9 items-center gap-2 text-xs text-muted">
          <input
            type="checkbox"
            className="h-3.5 w-3.5 accent-accent"
            checked={showHistorical}
            onChange={(event) => {
              setShowHistorical(event.target.checked)
              setPageNumber(0)
            }}
          />
          Show resolved &amp; historical findings
        </label>
      </div>

      {error && <ErrorState message={error} onRetry={() => void load()} className="mb-5" />}

      {isLoading ? (
        <SkeletonRows rows={6} />
      ) : findings.length === 0 ? (
        <EmptyState
          icon={ShieldCheck}
          title="Nothing to triage"
          description={
            severity === 'CRITICAL'
              ? 'No critical security findings are open. Try widening the severity filter to review lower-severity items.'
              : !showHistorical
                ? 'No security findings match these filters. Check "Show resolved & historical findings" to see past ones.'
                : 'No security findings match these filters.'
          }
        />
      ) : (
        <div className="flex flex-col gap-3">
          {findings.map((finding) => (
            <div key={finding.id} className="flex flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1 px-1 font-mono text-[11px] text-faint">
                <span className="truncate text-muted">{finding.repositoryFullName}</span>
                <span aria-hidden="true">·</span>
                <span>PR #{finding.pullRequestNumber}</span>
                <span aria-hidden="true">·</span>
                <span>{formatRelativeTime(finding.createdAt)}</span>
                {finding.pullRequestStatus !== 'OPEN' && (
                  <Chip tone={pullRequestStatusTone(finding.pullRequestStatus)}>
                    {humanize(finding.pullRequestStatus)}
                  </Chip>
                )}
              </div>
              <FindingCard
                ruleId={finding.ruleId}
                severityLabel={finding.severity}
                severityTone={severityTone(finding.severity)}
                category={finding.category}
                filePath={finding.filePath}
                lineNumber={finding.lineNumber}
                message={finding.message}
                recommendation={finding.recommendation}
                sourceUrl={githubLineUrl(
                  finding.repositoryFullName,
                  finding.commitSha,
                  finding.filePath,
                  finding.lineNumber,
                )}
                defaultExpanded={finding.severity === 'CRITICAL'}
              />
            </div>
          ))}

          {page && page.totalPages > 1 && (
            <div className="overflow-hidden rounded-lg border border-line bg-surface">
              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                totalElements={page.totalElements}
                first={page.first}
                last={page.last}
                onPrevious={() => setPageNumber((current) => Math.max(0, current - 1))}
                onNext={() => setPageNumber((current) => current + 1)}
              />
            </div>
          )}
        </div>
      )}
    </AppLayout>
  )
}
