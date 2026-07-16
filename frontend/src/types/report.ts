import type { AIReviewFinding } from './aiReviewFinding'
import type { AnalysisRunStatus, AnalysisRunTriggerReason, PullRequestReference, RepositoryReference } from './analysisRun'
import type { PolicyFinding } from './policyFinding'
import type { SecurityFinding } from './securityFinding'
import type { VerdictOutcome, VerdictReason } from './verdict'

export type AiReviewStatus = 'INCLUDED' | 'UNAVAILABLE' | 'DISABLED'

export interface AuditLogEntry {
  id: number
  eventType: string
  summary: string
  occurredAt: string
}

export interface ReportDetail {
  id: number
  analysisRunId: number
  analysisRunStatus: AnalysisRunStatus
  triggerReason: AnalysisRunTriggerReason
  commitSha: string
  analysisRunCreatedAt: string
  analysisRunUpdatedAt: string
  repository: RepositoryReference
  pullRequest: PullRequestReference
  policyFindings: PolicyFinding[]
  securityFindings: SecurityFinding[]
  aiReviewStatus: AiReviewStatus
  aiFindings: AIReviewFinding[]
  verdictOutcome: VerdictOutcome | null
  verdictReasons: VerdictReason[]
  auditTrail: AuditLogEntry[]
  publishedAt: string
}
