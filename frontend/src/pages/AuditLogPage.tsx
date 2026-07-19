import { useEffect, useState } from 'react'
import axios from 'axios'
import { AppLayout } from '../layouts/AppLayout'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { ErrorState } from '../components/ui/ErrorState'
import { Pagination } from '../components/ui/Pagination'
import { EmptyTableRow, Table, TableBody, TableHead } from '../components/ui/Table'
import { AUDIT_EVENT_TYPE_TONES } from '../components/ui/badgeTones'
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
 * Organization-scoped, read-only Audit Log search (Milestone 7). Gated by
 * AUDIT_LOG_READ - the frontend does not pre-compute who has it (same
 * posture as PolicyManagementPage/Milestone 5's review-decision form); a
 * caller without it sees a 403 surfaced as an error message instead of the
 * table. correlationId is deliberately not rendered as a column - it exists
 * in the model for future request-tracing but isn't a reviewer-facing
 * concept yet (see docs/API-Design.md).
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
    repositoryService.list().then(setRepositories).catch(() => setRepositories([]))
  }, [])

  useEffect(() => {
    setIsLoading(true)
    setError(null)
    auditLogService
      .search({
        eventType: eventType || undefined,
        repositoryId: repositoryId || undefined,
        page: pageNumber,
        size: PAGE_SIZE,
      })
      .then(setPage)
      .catch((err) => setError(describeError(err)))
      .finally(() => setIsLoading(false))
  }, [eventType, repositoryId, pageNumber])

  async function exportCsv() {
    setIsExporting(true)
    setError(null)
    try {
      const blob = await auditLogService.exportCsv({
        eventType: eventType || undefined,
        repositoryId: repositoryId || undefined,
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

  return (
    <AppLayout
      title="Audit Log"
      eyebrow="Governance"
      description="The authoritative history of governance actions across your organization - who did what, when, and to what."
      actions={
        <Button variant="secondary" onClick={() => void exportCsv()} disabled={isExporting}>
          {isExporting ? 'Exporting...' : 'Export CSV'}
        </Button>
      }
    >
      <div className="mb-4 flex flex-wrap gap-3">
        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={eventType}
          onChange={(event) => {
            setPageNumber(0)
            setEventType(event.target.value as AuditEventType | '')
          }}
        >
          <option value="">All event types</option>
          {EVENT_TYPE_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>

        <select
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-700"
          value={repositoryId}
          onChange={(event) => {
            setPageNumber(0)
            setRepositoryId(event.target.value ? Number(event.target.value) : '')
          }}
        >
          <option value="">All repositories</option>
          {repositories.map((repository) => (
            <option key={repository.id} value={repository.id}>
              {repository.fullName}
            </option>
          ))}
        </select>
      </div>

      {error && <ErrorState message={error} />}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <Table
          footer={
            page && (
              <Pagination
                page={page.page}
                totalPages={page.totalPages}
                first={page.first}
                last={page.last}
                onPrevious={() => setPageNumber((current) => current - 1)}
                onNext={() => setPageNumber((current) => current + 1)}
              />
            )
          }
        >
          <TableHead>
            <tr>
              <th className="px-4 py-2">Event</th>
              <th className="px-4 py-2">Summary</th>
              <th className="px-4 py-2">Actor</th>
              <th className="px-4 py-2">Repository</th>
              <th className="px-4 py-2">Occurred</th>
              <th className="px-4 py-2"></th>
            </tr>
          </TableHead>
          <TableBody>
            {page?.content.length ? (
              page.content.map((entry) => (
                <AuditLogRow
                  key={entry.id}
                  entry={entry}
                  expanded={expandedId === entry.id}
                  onToggle={() => setExpandedId((current) => (current === entry.id ? null : entry.id))}
                />
              ))
            ) : (
              <EmptyTableRow colSpan={6}>No audit log entries found.</EmptyTableRow>
            )}
          </TableBody>
        </Table>
      )}
    </AppLayout>
  )
}

function AuditLogRow({
  entry,
  expanded,
  onToggle,
}: {
  entry: AuditLogEntry
  expanded: boolean
  onToggle: () => void
}) {
  const hasDetail = entry.oldValue || entry.newValue || entry.targetType
  return (
    <>
      <tr className="hover:bg-slate-50">
        <td className="px-4 py-2">
          <Badge tone={AUDIT_EVENT_TYPE_TONES[entry.eventType]}>{entry.eventType}</Badge>
        </td>
        <td className="px-4 py-2 text-slate-700">{entry.summary}</td>
        <td className="px-4 py-2 text-slate-700">{entry.actorName ?? <span className="text-slate-400">System</span>}</td>
        <td className="px-4 py-2 text-slate-500">{entry.repositoryFullName ?? <span className="text-slate-400">&mdash;</span>}</td>
        <td className="px-4 py-2 text-slate-500">{new Date(entry.occurredAt).toLocaleString()}</td>
        <td className="px-4 py-2 text-right">
          {hasDetail && (
            <button type="button" onClick={onToggle} className="text-sm text-slate-500 hover:underline">
              {expanded ? 'Hide details' : 'Details'}
            </button>
          )}
        </td>
      </tr>
      {expanded && hasDetail && (
        <tr>
          <td colSpan={6} className="bg-slate-50 px-4 py-3 text-sm text-slate-700">
            <div className="flex flex-wrap gap-6">
              {entry.targetType && (
                <div>
                  <div className="font-medium text-slate-500">Target</div>
                  <div>
                    {entry.targetType} #{entry.targetId}
                  </div>
                </div>
              )}
              {entry.oldValue && (
                <div>
                  <div className="font-medium text-slate-500">Before</div>
                  <pre className="whitespace-pre-wrap font-mono text-xs">{JSON.stringify(entry.oldValue, null, 2)}</pre>
                </div>
              )}
              {entry.newValue && (
                <div>
                  <div className="font-medium text-slate-500">After</div>
                  <pre className="whitespace-pre-wrap font-mono text-xs">{JSON.stringify(entry.newValue, null, 2)}</pre>
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
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
