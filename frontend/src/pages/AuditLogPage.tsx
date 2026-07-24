import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import { Download, History } from 'lucide-react'
import { SettingsLayout } from '../layouts/SettingsLayout'
import { Chip } from '../components/ui/Chip'
import { Label, Select } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/states'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { buttonClasses } from '../components/ui/buttonClasses'
import { auditEventTone, humanize } from '../components/ui/tones'
import { formatDateTime, formatRelativeTime } from '../lib/format'
import { auditLogService } from '../services/auditLogService'
import { repositoryService } from '../services/repositoryService'
import type { ApiErrorResponse, PageResponse } from '../types/api'
import type { AuditEventType, AuditLogEntry } from '../types/auditLog'
import type { Repository } from '../types/repository'

const EVENT_TYPE_OPTIONS: AuditEventType[] = [
  'ENGINEERING_REPORT_PUBLISHED',
  'VERDICT_PRODUCED',
  'REVIEW_DECISION_RECORDED',
  'POLICY_CONFIGURATION_CHANGED',
  'REPOSITORY_CONNECTED',
  'REPOSITORY_UPDATED',
  'REPOSITORY_REMOVED',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_REMOVED',
  'ROLE_CREATED',
  'ROLE_UPDATED',
  'ROLE_REMOVED',
]

const PAGE_SIZE = 25

/**
 * The immutable governance history, reframed from a raw event table into a
 * readable timeline (Product Experience spec, §07). Behavior is unchanged -
 * same filters, same CSV export, same organization scoping, and still no
 * write path of any kind. correlationId remains deliberately unrendered: it
 * exists for request tracing, not as a reviewer-facing concept.
 */
export function AuditLogPage() {
  const [page, setPage] = useState<PageResponse<AuditLogEntry> | null>(null)
  const [repositories, setRepositories] = useState<Repository[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [eventType, setEventType] = useState<AuditEventType | ''>('')
  const [repositoryId, setRepositoryId] = useState<number | ''>('')
  const [pageNumber, setPageNumber] = useState(0)
  const [isExporting, setIsExporting] = useState(false)
  const [expandedId, setExpandedId] = useState<number | null>(null)

  useEffect(() => {
    repositoryService
      .list()
      .then(setRepositories)
      .catch(() => setRepositories([]))
  }, [])

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setPage(
        await auditLogService.search({
          eventType: eventType === '' ? undefined : eventType,
          repositoryId: repositoryId === '' ? undefined : repositoryId,
          page: pageNumber,
          size: PAGE_SIZE,
        }),
      )
    } catch (err) {
      setError(describeError(err))
    } finally {
      setIsLoading(false)
    }
  }, [eventType, repositoryId, pageNumber])

  useEffect(() => {
    void load()
  }, [load])

  async function exportCsv() {
    setIsExporting(true)
    setError(null)
    try {
      const blob = await auditLogService.exportCsv({
        eventType: eventType === '' ? undefined : eventType,
        repositoryId: repositoryId === '' ? undefined : repositoryId,
      })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = 'audit-log.csv'
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(describeError(err))
    } finally {
      setIsExporting(false)
    }
  }

  const entries = page?.content ?? []

  return (
    <SettingsLayout
      title="Audit Log"
      description="Every governance action GateKeeper has recorded, in order. Read-only by design."
      actions={
        <button type="button" className={buttonClasses('secondary', 'md')} onClick={() => void exportCsv()} disabled={isExporting}>
          <Download className="h-4 w-4" aria-hidden="true" />
          {isExporting ? 'Exporting…' : 'Export CSV'}
        </button>
      }
    >
      <div className="mb-5 flex flex-wrap gap-3">
        <div className="w-full sm:w-64">
          <Label htmlFor="eventType">Event type</Label>
          <Select
            id="eventType"
            value={eventType}
            onChange={(event) => {
              setEventType(event.target.value as AuditEventType | '')
              setPageNumber(0)
            }}
          >
            <option value="">All events</option>
            {EVENT_TYPE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {humanize(option)}
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
      </div>

      {error && <ErrorState message={error} onRetry={() => void load()} className="mb-5" />}

      {isLoading ? (
        <SkeletonRows rows={8} />
      ) : entries.length === 0 ? (
        <EmptyState
          icon={History}
          title="No audit entries"
          description="Nothing matches these filters yet. Governance actions are recorded here as they happen."
        />
      ) : (
        <div className="overflow-hidden rounded-lg border border-line bg-surface">
          <ul className="divide-y divide-line-soft">
            {entries.map((entry) => {
              const isExpanded = expandedId === entry.id
              const hasDetail = Boolean(entry.oldValue || entry.newValue)
              return (
                <li key={entry.id}>
                  <button
                    type="button"
                    onClick={() => setExpandedId(isExpanded ? null : entry.id)}
                    disabled={!hasDetail}
                    aria-expanded={hasDetail ? isExpanded : undefined}
                    className="flex w-full items-start gap-3 px-4 py-3.5 text-left transition-colors hover:bg-surface-2 disabled:cursor-default"
                  >
                    <Chip tone={auditEventTone(entry.eventType)} size="sm" className="mt-0.5 shrink-0">
                      {humanize(entry.eventType)}
                    </Chip>

                    <span className="min-w-0 flex-1">
                      <span className="block text-sm text-content">{entry.summary}</span>
                      <span className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 font-mono text-[11px] text-faint">
                        <span>{entry.actorName ?? 'System'}</span>
                        {entry.repositoryFullName && (
                          <>
                            <span aria-hidden="true">·</span>
                            <span className="truncate">{entry.repositoryFullName}</span>
                          </>
                        )}
                        {entry.pullRequestNumber !== null && (
                          <>
                            <span aria-hidden="true">·</span>
                            <span>PR #{entry.pullRequestNumber}</span>
                          </>
                        )}
                      </span>
                    </span>

                    <span
                      className="shrink-0 font-mono text-[11px] text-faint"
                      title={formatDateTime(entry.occurredAt)}
                    >
                      {formatRelativeTime(entry.occurredAt)}
                    </span>
                  </button>

                  {isExpanded && hasDetail && (
                    <div className="grid gap-3 border-t border-line-soft bg-surface-2 px-4 py-3.5 sm:grid-cols-2">
                      <ValueBlock label="Before" value={entry.oldValue} />
                      <ValueBlock label="After" value={entry.newValue} />
                    </div>
                  )}
                </li>
              )
            })}
          </ul>

          {page && (
            <Pagination
              page={page.page}
              totalPages={page.totalPages}
              totalElements={page.totalElements}
              first={page.first}
              last={page.last}
              onPrevious={() => setPageNumber((current) => Math.max(0, current - 1))}
              onNext={() => setPageNumber((current) => current + 1)}
            />
          )}
        </div>
      )}
    </SettingsLayout>
  )
}

function ValueBlock({ label, value }: { label: string; value: Record<string, unknown> | null }) {
  return (
    <div className="min-w-0">
      <p className="mb-1.5 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">{label}</p>
      {value ? (
        <pre className="overflow-x-auto rounded-md border border-line bg-surface p-2.5 font-mono text-[11px] text-muted">
          {JSON.stringify(value, null, 2)}
        </pre>
      ) : (
        <p className="text-xs text-faint">—</p>
      )}
    </div>
  )
}

function describeError(err: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(err) && err.response?.data?.error) {
    if (err.response.status === 403) {
      return 'You do not have permission to view the audit log.'
    }
    return err.response.data.error.message
  }
  return 'Failed to load the audit log.'
}
