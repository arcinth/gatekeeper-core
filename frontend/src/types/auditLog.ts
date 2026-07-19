export type AuditEventType =
  | 'ENGINEERING_REPORT_PUBLISHED'
  | 'VERDICT_PRODUCED'
  | 'REVIEW_DECISION_RECORDED'
  | 'POLICY_CONFIGURATION_CHANGED'
  | 'REPOSITORY_CONNECTED'
  | 'REPOSITORY_UPDATED'
  | 'REPOSITORY_REMOVED'
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_REMOVED'
  | 'ROLE_CREATED'
  | 'ROLE_UPDATED'
  | 'ROLE_REMOVED'

export type AuditTargetType = 'USER' | 'ROLE' | 'POLICY_RULE'

export interface AuditLogEntry {
  id: number
  eventType: AuditEventType
  summary: string
  organizationId: number
  repositoryId: number | null
  repositoryFullName: string | null
  pullRequestId: number | null
  pullRequestNumber: number | null
  analysisRunId: number | null
  actorId: number | null
  actorName: string | null
  targetType: AuditTargetType | null
  targetId: string | null
  oldValue: Record<string, unknown> | null
  newValue: Record<string, unknown> | null
  correlationId: string | null
  occurredAt: string
}

/** All fields optional - undefined means "not filtered on this criterion", mirroring PullRequestFilters' convention. */
export interface AuditLogFilters {
  eventType?: AuditEventType
  repositoryId?: number
  pullRequestId?: number
  analysisRunId?: number
  actorId?: number
  occurredAfter?: string
  occurredBefore?: string
  page?: number
  size?: number
}
