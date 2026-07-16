export type VerdictOutcome = 'APPROVED' | 'BLOCKED'

export interface VerdictReason {
  id: number
  ruleId: string
  message: string
  blocking: boolean
  createdAt: string
}

export interface VerdictSummary {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  outcome: VerdictOutcome
  createdAt: string
  reasonsTotal: number
}

export interface VerdictDetail {
  id: number
  analysisRunId: number
  repositoryFullName: string
  pullRequestNumber: number
  commitSha: string
  outcome: VerdictOutcome
  createdAt: string
  reasons: VerdictReason[]
}

export interface VerdictFilters {
  analysisRunId?: number
  repositoryId?: number
  outcome?: VerdictOutcome
  page?: number
  size?: number
  sort?: string
}
