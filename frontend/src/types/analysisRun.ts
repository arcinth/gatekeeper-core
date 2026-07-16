import type { PolicySeverity } from './policyFinding'
import type { SecuritySeverity } from './securityFinding'
import type { VerdictOutcome, VerdictReason } from './verdict'

export type AnalysisRunStatus = 'RECEIVED' | 'QUEUED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
export type AnalysisRunTriggerReason = 'OPENED' | 'REOPENED' | 'SYNCHRONIZE'
export type PullRequestStatus = 'OPEN' | 'CLOSED' | 'MERGED'

export interface AnalysisRunSummary {
  id: number
  repositoryId: number
  repositoryFullName: string
  pullRequestNumber: number
  pullRequestTitle: string
  commitSha: string
  status: AnalysisRunStatus
  triggerReason: AnalysisRunTriggerReason
  createdAt: string
  updatedAt: string
  findingsTotal: number
  securityFindingsTotal: number
  verdictOutcome: VerdictOutcome | null
}

export interface RepositoryReference {
  id: number
  fullName: string
}

export interface PullRequestReference {
  number: number
  title: string
  authorLogin: string
  sourceBranch: string
  targetBranch: string
  headSha: string
  status: PullRequestStatus
}

export interface AnalysisRunDetail {
  id: number
  repository: RepositoryReference
  pullRequest: PullRequestReference
  commitSha: string
  status: AnalysisRunStatus
  triggerReason: AnalysisRunTriggerReason
  failureReason: string | null
  createdAt: string
  updatedAt: string
  findingsBySeverity: Partial<Record<PolicySeverity, number>>
  securityFindingsBySeverity: Partial<Record<SecuritySeverity, number>>
  verdictOutcome: VerdictOutcome | null
  verdictReasons: VerdictReason[]
}

export interface AnalysisRunFilters {
  repositoryId?: number
  status?: AnalysisRunStatus
  triggerReason?: AnalysisRunTriggerReason
  page?: number
  size?: number
}
